package com.lirouti.domain.chat.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatMessage;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatQueryService 날짜 조회 테스트")
class ChatQueryServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Long MESSAGE_ID = 100L;
    private static final Long OLDER_MESSAGE_ID = 90L;

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatEmoticonRepository chatEmoticonRepository;
    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private MediaService mediaService;

    @InjectMocks
    private ChatQueryService chatQueryService;

    @Test
    @DisplayName("채팅 날짜 목록은 from을 포함하고 to를 제외한 KST 범위를 Repository에 전달한다")
    void getChatDates_Range_IsConvertedToKstDayBounds() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 9, 1);
        List<LocalDate> chatDates = List.of(
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 21)
        );
        when(chatMessageRepository.findChatDatesByGroupIdAndCreatedAtRange(
                GROUP_ID,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0)
        )).thenReturn(chatDates);

        List<LocalDate> result = chatQueryService.getChatDates(
                MEMBER_ID, GROUP_ID, from, to);

        assertThat(result).containsExactlyElementsOf(chatDates);
        verify(chatMessageRepository).findChatDatesByGroupIdAndCreatedAtRange(
                GROUP_ID,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );
    }

    @Test
    @DisplayName("끝 날짜가 시작 날짜보다 앞서거나 같으면 날짜 범위를 거부한다")
    void getChatDates_InvalidRange_ThrowsBadRequest() {
        LocalDate date = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> chatQueryService.getChatDates(
                MEMBER_ID, GROUP_ID, date, date
        )).isInstanceOf(GeneralException.class);

        verify(chatMessageRepository, never())
                .findChatDatesByGroupIdAndCreatedAtRange(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    @DisplayName("선택 날짜에 채팅이 없으면 빈 결과와 hasChat false를 반환한다")
    void getMessages_EmptyDate_ReturnsNoChat() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        when(chatMessageRepository
                .existsByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        GROUP_ID,
                        LocalDateTime.of(2026, 8, 22, 0, 0),
                        LocalDateTime.of(2026, 8, 23, 0, 0)
                )).thenReturn(false);

        ChatResDTO.MessageList result = chatQueryService.getMessages(
                MEMBER_ID, GROUP_ID, date, null, 30);

        assertThat(result.messages()).isEmpty();
        assertThat(result.date()).isEqualTo(date);
        assertThat(result.hasChat()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
        verify(chatMessageRepository, never()).findMessagesByGroupIdAndCreatedAtRange(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("선택 날짜의 마지막 페이지도 이전 날짜 메시지가 있으면 다음 cursor를 유지한다")
    void getMessages_SelectedDateWithOlderMessages_ContinuesWithCursor() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        LocalDateTime dateStart = LocalDateTime.of(2026, 8, 21, 0, 0);
        LocalDateTime nextDateStart = LocalDateTime.of(2026, 8, 22, 0, 0);
        ChatMessage selectedDateMessage = message(MESSAGE_ID, "선택 날짜 메시지");
        ChatMessage olderMessage = message(OLDER_MESSAGE_ID, "이전 날짜 메시지");

        when(chatMessageRepository
                .existsByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        GROUP_ID, dateStart, nextDateStart
                )).thenReturn(true);
        when(chatMessageRepository.existsByGroupIdAndCreatedAtLessThan(
                GROUP_ID, dateStart
        )).thenReturn(true);
        when(chatMessageRepository.findMessagesByGroupIdAndCreatedAtRange(
                GROUP_ID,
                null,
                dateStart,
                nextDateStart,
                PageRequest.of(0, 31)
        )).thenReturn(List.of(selectedDateMessage));

        ChatResDTO.MessageList firstPage = chatQueryService.getMessages(
                MEMBER_ID, GROUP_ID, date, null, 30);

        assertThat(firstPage.messages()).extracting(ChatResDTO.Message::id)
                .containsExactly(MESSAGE_ID);
        assertThat(firstPage.nextCursor()).isEqualTo(MESSAGE_ID);
        assertThat(firstPage.hasNext()).isTrue();

        when(chatMessageRepository.findMessagesByGroupIdAndCreatedAtRange(
                GROUP_ID,
                MESSAGE_ID,
                null,
                nextDateStart,
                PageRequest.of(0, 31)
        )).thenReturn(List.of(olderMessage));

        ChatResDTO.MessageList secondPage = chatQueryService.getMessages(
                MEMBER_ID, GROUP_ID, date, MESSAGE_ID, 30);

        assertThat(secondPage.messages()).extracting(ChatResDTO.Message::id)
                .containsExactly(OLDER_MESSAGE_ID);
        assertThat(secondPage.date()).isEqualTo(date);
        assertThat(secondPage.hasChat()).isTrue();
        verify(chatMessageRepository).findMessagesByGroupIdAndCreatedAtRange(
                GROUP_ID,
                MESSAGE_ID,
                null,
                nextDateStart,
                PageRequest.of(0, 31)
        );
    }

    @Test
    @DisplayName("메시지 목록은 답장 원본의 미리보기를 함께 반환한다")
    void getMessages_WithReply_ReturnsReplyPreview() {
        ChatMessage message = message(MESSAGE_ID, "답장 메시지");
        ChatMessage original = replyMessage(80L, "원본 메시지");
        when(message.getReplyToMessage()).thenReturn(original);
        when(chatMessageRepository.findMessagesByGroupId(
                GROUP_ID,
                null,
                PageRequest.of(0, 31)
        )).thenReturn(List.of(message));

        ChatResDTO.MessageList result = chatQueryService.getMessages(
                MEMBER_ID, GROUP_ID, null, 30);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).reply()).isNotNull();
        assertThat(result.messages().get(0).reply().id()).isEqualTo(80L);
        assertThat(result.messages().get(0).reply().content()).isEqualTo("원본 메시지");
    }

    private ChatMessage message(Long id, String content) {
        ChatMessage message = mock(ChatMessage.class);
        Group group = mock(Group.class);
        Member sender = mock(Member.class);
        when(message.getId()).thenReturn(id);
        when(message.getGroup()).thenReturn(group);
        when(message.getSender()).thenReturn(sender);
        when(message.getMessageType()).thenReturn(ChatMessageType.TEXT);
        when(message.getContent()).thenReturn(content);
        when(message.getEmoticonId()).thenReturn(null);
        when(message.getClientMessageId()).thenReturn("client-" + id);
        when(message.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 21, 12, 0));
        when(group.getId()).thenReturn(GROUP_ID);
        when(sender.getId()).thenReturn(MEMBER_ID);
        when(sender.getNickname()).thenReturn("채팅 사용자");
        return message;
    }

    private ChatMessage replyMessage(Long id, String content) {
        ChatMessage message = mock(ChatMessage.class);
        Member sender = mock(Member.class);
        when(message.getId()).thenReturn(id);
        when(message.getSender()).thenReturn(sender);
        when(message.getMessageType()).thenReturn(ChatMessageType.TEXT);
        when(message.getContent()).thenReturn(content);
        when(message.getEmoticonId()).thenReturn(null);
        when(message.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 21, 11, 0));
        when(sender.getId()).thenReturn(MEMBER_ID);
        when(sender.getNickname()).thenReturn("원본 작성자");
        return message;
    }
}
