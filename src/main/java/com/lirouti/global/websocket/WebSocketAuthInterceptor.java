package com.lirouti.global.websocket;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final String TOPIC_CHAT_PREFIX = "/topic/groups/";
    private static final String APP_CHAT_PREFIX = "/app/groups/";
    private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
    private static final String CHAT_SUFFIX = "/chat";
    private static final String CHAT_MESSAGE_SUFFIX = "/chat/messages";

    private final MemberQueryService memberQueryService;
    private final GroupValidationService groupValidationService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            validateAuthenticatedMember(accessor, message);
            return message;
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            if (USER_ERROR_DESTINATION.equals(accessor.getDestination())) {
                validateAuthenticatedMember(accessor, message);
            } else {
                validateGroupAccess(accessor, message, TOPIC_CHAT_PREFIX, CHAT_SUFFIX);
            }
        } else if (accessor.getCommand() == StompCommand.SEND) {
            validateGroupAccess(accessor, message, APP_CHAT_PREFIX, CHAT_MESSAGE_SUFFIX);
        }

        return message;
    }

    private void validateAuthenticatedMember(
            StompHeaderAccessor accessor,
            Message<?> message
    ) {
        CustomUserDetails userDetails = getUserDetails(accessor, message);

        // 연결 이후 탈퇴한 회원이 기존 socket을 재사용하지 못하게 상태를 재검증한다.
        memberQueryService.getActiveMember(userDetails.getMemberId());
    }

    private void validateGroupAccess(
            StompHeaderAccessor accessor,
            Message<?> message,
            String prefix,
            String suffix
    ) {
        CustomUserDetails userDetails = getUserDetails(accessor, message);
        Long groupId = parseGroupId(accessor.getDestination(), prefix, suffix, message);

        // GroupValidationService가 회원·그룹·참여 상태를 함께 검증하므로 별도 회원 조회를 반복하지 않는다.
        groupValidationService.validateActiveGroupMember(groupId, userDetails.getMemberId());
    }

    private CustomUserDetails getUserDetails(
            StompHeaderAccessor accessor,
            Message<?> message
    ) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new MessagingException(message, "WebSocket 인증 정보가 없습니다.");
        }
        return userDetails;
    }

    private Long parseGroupId(
            String destination,
            String prefix,
            String suffix,
            Message<?> message
    ) {
        if (destination == null
                || !destination.startsWith(prefix)
                || !destination.endsWith(suffix)) {
            throw new MessagingException(message, "채팅 destination이 올바르지 않습니다.");
        }

        int groupIdStart = prefix.length();
        int groupIdEnd = destination.length() - suffix.length();
        String groupIdValue = destination.substring(groupIdStart, groupIdEnd);

        if (groupIdValue.isBlank() || groupIdValue.indexOf('/') >= 0) {
            throw new MessagingException(message, "채팅 그룹 ID가 올바르지 않습니다.");
        }

        try {
            long groupId = Long.parseLong(groupIdValue);
            if (groupId < 1) {
                throw new NumberFormatException();
            }
            return groupId;
        } catch (NumberFormatException e) {
            throw new MessagingException(message, "채팅 그룹 ID가 올바르지 않습니다.");
        }
    }
}
