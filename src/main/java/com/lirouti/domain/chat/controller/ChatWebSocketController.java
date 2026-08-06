package com.lirouti.domain.chat.controller;

import java.security.Principal;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
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
        ChatResDTO.Message response = chatCommandService.sendMessage(memberId, groupId, request);

        // Service 트랜잭션이 반환된 뒤 전송하므로 DB 저장 성공 후에만 broadcast한다.
        messagingTemplate.convertAndSend(CHAT_TOPIC_FORMAT.formatted(groupId), response);
    }

    @MessageExceptionHandler(ChatException.class)
    public void handleRecoverableDomainException(
            GeneralException exception,
            @Payload ChatReqDTO.SendMessage request,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return;
        }

        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new StompErrorResponse(
                        exception.getCode().getCode(),
                        exception.getCode().getMessage(),
                        request == null ? null : request.clientMessageId())
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
    private record StompErrorResponse(
            String code,
            String message,
            String clientMessageId
    ) {
    }
}
