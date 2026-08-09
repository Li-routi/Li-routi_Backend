package com.lirouti.domain.chat.service.command;

import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.lirouti.domain.chat.converter.ChatConverter;
import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.result.ChatSendResult;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.entity.ChatMessage;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.chat.repository.ChatReadRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatCommandService {
    private static final String EMOTICON_CODE_UNIQUE_CONSTRAINT =
            "uk_chat_emoticon_code";

    private final ChatMessageRepository chatMessageRepository;
    private final ChatEmoticonRepository chatEmoticonRepository;
    private final ChatReadRepository chatReadRepository;
    private final GroupValidationService groupValidationService;
    private final MediaService mediaService;
    private final GroupMemberRepository groupMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 검증과 S3 업로드가 끝난 이모티콘 메타데이터를 저장한다.
     * flush 시점의 code unique 충돌은 동시 등록까지 포함해 도메인 예외로 변환한다.
     */
    @Transactional
    public ChatEmoticon createEmoticonMetadata(
            ChatReqDTO.RegisterEmoticon request,
            String assetKey,
            String contentType
    ) {
        ChatEmoticon emoticon = ChatConverter.toEntity(
                request,
                assetKey,
                contentType
        );
        try {
            return chatEmoticonRepository.saveAndFlush(emoticon);
        } catch (DataIntegrityViolationException e) {
            if (isEmoticonCodeUniqueViolation(e)) {
                throw new ChatException(ChatErrorCode.DUPLICATE_EMOTICON_CODE);
            }
            throw e;
        }
    }

    /**
     * 이모티콘 상태를 같은 값으로 반복 요청해도 동일한 결과가 되도록 변경한다.
     * 활성화 호출자는 이 transaction을 열기 전에 저장된 S3 object 검증을 끝내야 한다.
     */
    @Transactional
    public void updateEmoticonStatus(Long emoticonId, boolean active) {
        ChatEmoticon emoticon = chatEmoticonRepository.findById(emoticonId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.EMOTICON_NOT_FOUND));
        if (active) {
            emoticon.activate();
            return;
        }
        emoticon.deactivate();
    }

    /**
     * 활성 그룹 멤버의 메시지를 저장하고 서버 기준 응답을 반환한다.
     * 같은 그룹·발신자의 clientMessageId가 이미 존재하면 payload를 확인한 뒤 재전송 결과를 반환한다.
     */
    @Transactional
    public ChatSendResult sendMessage(
            Long memberId,
            Long groupId,
            ChatReqDTO.SendMessage request
    ) {
        validateMessageShape(request);

        // 탈퇴·강퇴와 같은 그룹 행을 먼저 잠가 권한 회수 이후 메시지가 저장되는 경쟁 조건을 막는다.
        groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateActiveGroupMember(groupId, memberId);

        ChatMessage existing = chatMessageRepository
                .findByGroupIdAndSenderIdAndClientMessageId(
                        groupId,
                        memberId,
                        request.clientMessageId())
                .orElse(null);

        if (existing != null) {
            if (!hasSamePayload(existing, request)) {
                throw new ChatException(ChatErrorCode.CLIENT_MESSAGE_ID_INVALID);
            }
            return new ChatSendResult(toMessageResponse(existing), false);
        }

        ChatEmoticon emoticon = resolveActiveEmoticon(request);
        int insertedCount = chatMessageRepository.insertIfAbsent(
                groupId,
                memberId,
                request.clientMessageId(),
                request.type().name(),
                request.content(),
                emoticon == null ? null : emoticon.getId()
        );

        ChatMessage message = chatMessageRepository
                .findByGroupIdAndSenderIdAndClientMessageId(
                        groupId,
                        memberId,
                        request.clientMessageId())
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (!hasSamePayload(message, request)) {
            throw new ChatException(ChatErrorCode.CLIENT_MESSAGE_ID_INVALID);
        }
        if (insertedCount == 1 && groupMemberRepository != null && eventPublisher != null) {
            String senderName = groupValidationService.validateActiveGroupMember(groupId, memberId)
                    .getMember().getNickname();
            String preview = truncateForNotificationBody(
                    request.type() == ChatMessageType.TEXT ? request.content() : "이모티콘을 보냈어요.");
            groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE).stream()
                    .map(groupMember -> groupMember.getMember().getId())
                    .filter(recipientId -> !recipientId.equals(memberId))
                    .forEach(recipientId -> eventPublisher.publishEvent(new NotificationRequestedEvent(
                            recipientId, NotificationCategory.CHAT, NotificationType.GROUP_CHAT_MESSAGE,
                            senderName + "님의 새 메시지", preview, groupId, message.getId(),
                            "CHAT_MESSAGE", "chat-message:" + message.getId() + ":" + recipientId)));
        }
        return new ChatSendResult(toMessageResponse(message), insertedCount == 1);
    }

    /**
     * notification.body는 VARCHAR(255)다. 채팅 본문은 최대 2000자까지 허용되므로,
     * 그대로 넣으면 알림 저장이 DataIntegrityViolationException으로 조용히 실패한다.
     */
    private static final int NOTIFICATION_BODY_MAX_LENGTH = 255;

    private String truncateForNotificationBody(String text) {
        if (text.length() <= NOTIFICATION_BODY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, NOTIFICATION_BODY_MAX_LENGTH - 1) + "…";
    }

    /**
     * 같은 clientMessageId를 재사용한 요청인지, 동일 메시지의 재전송인지 구분한다.
     */
    private boolean hasSamePayload(
            ChatMessage existing,
            ChatReqDTO.SendMessage request
    ) {
        if (existing.getMessageType() != request.type()
                || !Objects.equals(existing.getContent(), request.content())) {
            return false;
        }

        if (request.type() == ChatMessageType.TEXT) {
            return existing.getEmoticonId() == null;
        }

        return existing.getEmoticonId() != null
                && chatEmoticonRepository.findById(existing.getEmoticonId())
                .map(emoticon -> emoticon.getCode().equals(request.emoticonCode()))
                .orElse(false);
    }

    /**
     * 활성 그룹 멤버의 읽음 위치를 요청 메시지까지 전진시킨다.
     */
    @Transactional
    public void updateReadPosition(
            Long memberId,
            Long groupId,
            ChatReqDTO.UpdateRead request
    ) {
        groupValidationService.validateActiveGroupMember(groupId, memberId);
        if (request == null
                || request.lastReadMessageId() == null
                || request.lastReadMessageId() < 1) {
            throw new ChatException(ChatErrorCode.READ_POSITION_INVALID);
        }

        ChatMessage message = chatMessageRepository
                .findByIdAndGroupId(request.lastReadMessageId(), groupId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.MESSAGE_NOT_FOUND));
        chatReadRepository.upsertIfAhead(
                groupId,
                memberId,
                message.getId(),
                LocalDateTime.now()
        );
    }

    /**
     * DB와 직접 연결되지 않은 요청 구조를 Service 진입점에서도 검증한다.
     */
    private void validateMessageShape(ChatReqDTO.SendMessage request) {
        if (request == null
                || request.clientMessageId() == null
                || request.clientMessageId().isBlank()
                || request.clientMessageId().length() > ChatMessage.MAX_CLIENT_MESSAGE_ID_LENGTH) {
            throw new ChatException(ChatErrorCode.CLIENT_MESSAGE_ID_INVALID);
        }

        if (request.type() == null) {
            throw new ChatException(ChatErrorCode.MESSAGE_TYPE_INVALID);
        }

        if (request.type() == ChatMessageType.TEXT) {
            validateTextMessage(request);
            return;
        }

        if (request.content() != null || request.emoticonCode() == null
                || request.emoticonCode().isBlank()
                || request.emoticonCode().length() > 100) {
            throw new ChatException(ChatErrorCode.EMOTICON_CODE_INVALID);
        }
    }

    /**
     * TEXT 메시지는 본문만 가지며 비어 있는 문자열을 저장하지 않는다.
     */
    private void validateTextMessage(ChatReqDTO.SendMessage request) {
        if (request.content() == null
                || request.content().isBlank()
                || request.content().length() > ChatMessage.MAX_CONTENT_LENGTH) {
            throw new ChatException(ChatErrorCode.MESSAGE_CONTENT_INVALID);
        }
        if (request.emoticonCode() != null) {
            throw new ChatException(ChatErrorCode.MESSAGE_TYPE_INVALID);
        }
    }

    /**
     * 신규 EMOTICON 메시지에 사용할 활성 자산을 코드로 조회한다.
     */
    private ChatEmoticon resolveActiveEmoticon(ChatReqDTO.SendMessage request) {
        if (request.type() == ChatMessageType.TEXT) {
            return null;
        }
        return chatEmoticonRepository.findByCodeAndActiveTrue(request.emoticonCode())
                .orElseThrow(() -> new ChatException(ChatErrorCode.EMOTICON_NOT_FOUND));
    }

    /**
     * 저장된 메시지의 이모티콘 ID를 조회 시점 응답 정보로 변환한다.
     */
    private ChatResDTO.Message toMessageResponse(ChatMessage message) {
        ChatEmoticon emoticon = message.getEmoticonId() == null
                ? null
                : chatEmoticonRepository.findById(message.getEmoticonId()).orElse(null);
        return toMessageResponse(message, emoticon);
    }

    /**
     * 메시지와 이미 조회한 이모티콘 자산을 API 응답으로 조립한다.
     */
    private ChatResDTO.Message toMessageResponse(
            ChatMessage message,
            ChatEmoticon emoticon
    ) {
        ChatResDTO.Emoticon responseEmoticon = emoticon == null
                ? null
                : ChatConverter.toEmoticon(
                        emoticon,
                        mediaService.resolveViewUrl(
                                emoticon.getAssetKey(),
                                MediaPurpose.CHAT_EMOTICON));
                                
        return ChatConverter.toMessage(message, responseEmoticon);
    }

    private boolean isEmoticonCodeUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && constraintViolation.getKind()
                    == ConstraintViolationException.ConstraintKind.UNIQUE
                    && matchesEmoticonCodeConstraint(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean matchesEmoticonCodeConstraint(String constraintName) {
        return EMOTICON_CODE_UNIQUE_CONSTRAINT.equals(constraintName)
                || constraintName != null
                && constraintName.endsWith("." + EMOTICON_CODE_UNIQUE_CONSTRAINT);
    }
}
