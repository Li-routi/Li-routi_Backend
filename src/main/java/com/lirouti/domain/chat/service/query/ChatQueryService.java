package com.lirouti.domain.chat.service.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.chat.cache.ChatEmoticonCacheReader;
import com.lirouti.domain.chat.cache.ChatEmoticonCacheReader.CachedEmoticon;
import com.lirouti.domain.chat.converter.ChatConverter;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.entity.ChatMessage;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatQueryService {
    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChatMessageRepository chatMessageRepository;
    private final ChatEmoticonRepository chatEmoticonRepository;
    private final ChatEmoticonCacheReader chatEmoticonCacheReader;
    private final GroupValidationService groupValidationService;
    private final MemberQueryService memberQueryService;
    private final MediaService mediaService;

    /**
     * 활성 그룹 멤버가 과거 채팅 메시지를 cursor 기준으로 조회한다.
     */
    @Transactional(readOnly = true)
    public ChatResDTO.MessageList getMessages(
            Long memberId,
            Long groupId,
            Long cursor,
            Integer size
    ) {
        return getMessages(memberId, groupId, null, cursor, size);
    }

    /**
     * 선택한 날짜의 메시지부터 과거 방향으로 cursor 조회를 이어간다.
     * 첫 요청은 선택 날짜만, 다음 요청부터는 해당 날짜 이전까지 조회한다.
     */
    @Transactional(readOnly = true)
    public ChatResDTO.MessageList getMessages(
            Long memberId,
            Long groupId,
            LocalDate date,
            Long cursor,
            Integer size
    ) {
        groupValidationService.validateActiveGroupMember(groupId, memberId);

        int appliedSize = clampSize(size);
        boolean hasChat = date == null;
        boolean hasOlderMessages = false;
        LocalDateTime fromInclusive = null;
        LocalDateTime toExclusive = null;

        if (date != null) {
            fromInclusive = startOfDay(date);
            toExclusive = startOfDay(date.plusDays(1));
            hasChat = chatMessageRepository
                    .existsByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            groupId,
                            fromInclusive,
                            toExclusive);
            if (!hasChat) {
                return emptyMessageList(date, false);
            }
            if (cursor == null) {
                hasOlderMessages = chatMessageRepository
                        .existsByGroupIdAndCreatedAtLessThan(groupId, fromInclusive);
            } else {
                fromInclusive = null;
            }
        }

        List<ChatMessage> rows = date == null
                ? chatMessageRepository.findMessagesByGroupId(
                        groupId,
                        cursor,
                        PageRequest.of(0, appliedSize + 1))
                : chatMessageRepository.findMessagesByGroupIdAndCreatedAtRange(
                        groupId,
                        cursor,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, appliedSize + 1));

        CursorPage<ChatMessage> page = sliceByCursor(rows, appliedSize, ChatMessage::getId);

        List<ChatMessage> messages = new ArrayList<>(page.rows());
        Collections.reverse(messages);
        Map<Long, ChatResDTO.Emoticon> emoticons = toMessageEmoticons(page.rows());

        boolean hasNext = page.hasNext() || hasOlderMessages;
        Long nextCursor = page.nextCursor();
        if (hasOlderMessages && nextCursor == null && !page.rows().isEmpty()) {
            nextCursor = page.rows().get(page.rows().size() - 1).getId();
        }
        return ChatConverter.toMessageList(
                messages,
                emoticons,
                nextCursor,
                hasNext,
                date,
                date == null ? !page.rows().isEmpty() : hasChat
        );
    }

    /**
     * 활성 그룹 멤버가 지정한 KST 날짜 범위에서 채팅이 존재하는 날짜를 조회한다.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> getChatDates(
            Long memberId,
            Long groupId,
            LocalDate from,
            LocalDate to
    ) {
        groupValidationService.validateActiveGroupMember(groupId, memberId);
        if (from == null || to == null || !from.isBefore(to)) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        return chatMessageRepository.findChatDatesByGroupIdAndCreatedAtRange(
                groupId,
                startOfDay(from),
                startOfDay(to)
        );
    }

    /**
     * 활성 회원이 선택할 수 있는 서비스 이모티콘 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public ChatResDTO.EmoticonList getEmoticons(Long memberId) {
        memberQueryService.getActiveMember(memberId);

        List<CachedEmoticon> emoticons = chatEmoticonCacheReader.getActive();

        Map<Long, String> assetUrls = resolveCachedEmoticonAssetUrls(emoticons);
        return ChatConverter.toEmoticonListFromCache(emoticons, assetUrls);
    }

    /**
     * JWT role이 변경 전 값일 수 있으므로 DB의 현재 활성 상태와 관리자 role을 다시 검증한다.
     * 운영 화면에는 비활성 자산도 필요하므로 일반 회원 목록과 달리 전체를 조회한다.
     */
    @Transactional(readOnly = true)
    public ChatResDTO.AdminEmoticonList getAdminEmoticons(Long memberId) {
        Member member = memberQueryService.getActiveMember(memberId);
        if (member.getRole() != Role.ROLE_ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

        List<ChatEmoticon> emoticons =
                chatEmoticonRepository.findAllByOrderByDisplayOrderAscIdAsc();
        Map<Long, String> assetUrls = resolveEmoticonAssetUrls(emoticons);
        return ChatConverter.toAdminEmoticonList(emoticons, assetUrls);
    }

    /**
     * 메시지에 연결된 이모티콘을 비활성 상태와 관계없이 조회해 기존 메시지 자산을 보존한다.
     */
    private Map<Long, ChatResDTO.Emoticon> toMessageEmoticons(List<ChatMessage> messages) {
        List<Long> emoticonIds = messages.stream()
                .flatMap(message -> Stream.of(
                        message.getEmoticonId(),
                        message.getReplyToMessage() == null
                                ? null
                                : message.getReplyToMessage().getEmoticonId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (emoticonIds.isEmpty()) {
            return Map.of();
        }

        return chatEmoticonRepository.findAllById(emoticonIds).stream()
                .collect(Collectors.toMap(
                        ChatEmoticon::getId,
                        emoticon -> ChatConverter.toEmoticon(
                                emoticon,
                                mediaService.resolveViewUrl(
                                        emoticon.getAssetKey(),
                                        MediaPurpose.CHAT_EMOTICON)
                )));
    }

    private Map<Long, String> resolveEmoticonAssetUrls(List<ChatEmoticon> emoticons) {
        return emoticons.stream()
                .collect(Collectors.toMap(
                        ChatEmoticon::getId,
                        emoticon -> mediaService.resolveViewUrl(
                                emoticon.getAssetKey(),
                                MediaPurpose.CHAT_EMOTICON)
                ));
    }

    private Map<Long, String> resolveCachedEmoticonAssetUrls(List<CachedEmoticon> emoticons) {
        return emoticons.stream()
                .collect(Collectors.toMap(
                        CachedEmoticon::id,
                        emoticon -> mediaService.resolveViewUrl(
                                emoticon.assetKey(),
                                MediaPurpose.CHAT_EMOTICON)
                ));
    }

    /**
     * 저장소가 반환한 size + 1개 결과에서 응답 목록과 다음 cursor를 계산한다.
     */
    private static <T> CursorPage<T> sliceByCursor(
            List<T> rows,
            int size,
            Function<T, Long> idExtractor
    ) {
        boolean hasNext = rows.size() > size;
        List<T> pageRows = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = hasNext && !pageRows.isEmpty()
                ? idExtractor.apply(pageRows.get(pageRows.size() - 1))
                : null;
        return new CursorPage<>(pageRows, nextCursor, hasNext);
    }

    /**
     * 요청하지 않은 크기는 기본값으로 처리하고 서버 최대 크기를 넘지 않게 제한한다.
     */
    private static int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(KST).toLocalDateTime();
    }

    private static ChatResDTO.MessageList emptyMessageList(
            LocalDate date,
            boolean hasChat
    ) {
        return ChatResDTO.MessageList.builder()
                .messages(List.of())
                .nextCursor(null)
                .hasNext(false)
                .date(date)
                .hasChat(hasChat)
                .build();
    }
 
    private record CursorPage<T>(
        List<T> rows,
        Long nextCursor,
        boolean hasNext
    ) {
    }
}
