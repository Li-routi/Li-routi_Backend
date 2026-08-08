package com.lirouti.domain.chat.controller;

import java.security.Principal;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.result.ChatSendResult;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.apiPayload.exception.GeneralException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@Validated
@RequiredArgsConstructor
public class ChatWebSocketController {
    private static final String CHAT_TOPIC_FORMAT = "/topic/groups/%d/chat";

    private final ChatCommandService chatCommandService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/groups/{groupId}/chat/messages")
    public void sendMessage(
            @DestinationVariable Long groupId,
            @Valid @Payload ChatReqDTO.SendMessage request,
            Principal principal
    ) {
        Long memberId = getMemberId(principal);
        ChatSendResult result = chatCommandService.sendMessage(
                memberId,
                groupId,
                request
        );

        if (result.newlyCreated()) {
            messagingTemplate.convertAndSend(
                    CHAT_TOPIC_FORMAT.formatted(groupId),
                    result.message()
            );
        }
    }

    // clientMessageId 오류는 요청 기기의 pending 메시지에 귀속되므로 다른 세션에 broadcast하지 않는다.
    @MessageExceptionHandler(ChatException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public StompErrorResponse handleRecoverableDomainException(
            GeneralException exception,
            @Payload ChatReqDTO.SendMessage request
    ) {
        return new StompErrorResponse(
                exception.getCode().getCode(),
                exception.getCode().getMessage(),
                request == null ? null : request.clientMessageId()
        );
    }

    private Long getMemberId(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new MessagingException("WebSocket 인증 정보가 없습니다.");
        }

        return userDetails.getMemberId();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StompErrorResponse(
            String code,
            String message,
            String clientMessageId
    ) {
    }
}
