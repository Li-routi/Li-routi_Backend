package com.lirouti.global.websocket;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP {@code GeneralExceptionAdvice}가 처리하지 못하는 STOMP client frame 오류를
 * 클라이언트가 해석할 수 있는 ERROR payload로 변환한다.
 */
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {
    private static final String USER_ERROR_DESTINATION = "/queue/errors";

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public StompErrorHandler(
            ObjectMapper objectMapper,
            @Lazy SimpMessagingTemplate messagingTemplate
    ) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        StompHeaderAccessor clientAccessor = getAccessor(clientMessage);
        BaseErrorCode errorCode = resolveErrorCode(exception);
        String errorMessage = resolveErrorMessage(exception, errorCode);

        if (isRecoverableDomainException(exception)
                && sendRecoverableError(clientAccessor, errorCode, errorMessage, clientMessage)) {
            return null;
        }

        return buildErrorMessage(clientAccessor, errorCode, errorMessage, clientMessage);
    }

    private Message<byte[]> buildErrorMessage(
            StompHeaderAccessor clientAccessor,
            BaseErrorCode errorCode,
            String errorMessage,
            Message<byte[]> clientMessage
    ) {
        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);

        errorAccessor.setMessage(errorMessage);
        errorAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        if (clientAccessor != null && clientAccessor.getReceipt() != null) {
            errorAccessor.setReceiptId(clientAccessor.getReceipt());
        }

        // 재전송 중인 메시지를 클라이언트가 같은 pending 항목과 연결할 수 있도록 보존한다.
        byte[] payload = toPayload(
                new StompErrorResponse(
                        errorCode.getCode(),
                        errorMessage,
                        resolveClientMessageId(clientMessage))
        );
        return MessageBuilder.createMessage(payload, errorAccessor.getMessageHeaders());
    }

    private boolean sendRecoverableError(
            StompHeaderAccessor clientAccessor,
            BaseErrorCode errorCode,
            String errorMessage,
            Message<byte[]> clientMessage
    ) {
        if (clientAccessor == null) {
            return false;
        }

        Principal user = clientAccessor.getUser();
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return false;
        }

        messagingTemplate.convertAndSendToUser(
                user.getName(),
                USER_ERROR_DESTINATION,
                new StompErrorResponse(
                        errorCode.getCode(),
                        errorMessage,
                        resolveClientMessageId(clientMessage))
        );
        return true;
    }

    private boolean isRecoverableDomainException(Throwable exception) {
        return findCause(exception, ChatException.class) != null;
    }

    private BaseErrorCode resolveErrorCode(Throwable exception) {
        // Service에서 발생한 도메인 코드는 유지하고, STOMP 계층 오류만 공통 코드로 보정한다.
        GeneralException generalException = findGeneralException(exception);
        if (generalException != null) {
            return generalException.getCode();
        }
        return findMessagingException(exception) != null
                ? GeneralErrorCode.BAD_REQUEST
                : GeneralErrorCode.INTERNAL_SERVER_ERROR;
    }

    // 예외 메시지에 도메인 코드가 포함되어 있으면 그대로 사용하고, 그렇지 않으면 공통 메시지를 사용한다.
    private String resolveErrorMessage(Throwable exception, BaseErrorCode errorCode) {
        if (findGeneralException(exception) != null) {
            return errorCode.getMessage();
        }
        MessagingException messagingException = findMessagingException(exception);
        return messagingException == null || messagingException.getMessage() == null
                ? errorCode.getMessage()
                : messagingException.getMessage();
    }

    private GeneralException findGeneralException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof GeneralException generalException) {
                return generalException;
            }
            current = current.getCause();
        }
        return null;
    }

    private MessagingException findMessagingException(Throwable exception) {
        Throwable current = exception;

        // MessagingException이 발생한 경우, 그 원인을 추적하여 가장 가까운 MessagingException을 반환한다.
        while (current != null) {
            if (current instanceof MessagingException messagingException) {
                return messagingException;
            }
            current = current.getCause();
        }
        return null;
    }

    private <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private StompHeaderAccessor getAccessor(Message<byte[]> message) {
        if (message == null) {
            return null;
        }
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }

    // 클라이언트가 전송한 메시지에 clientMessageId가 포함되어 있으면 그대로 반환하고, 없으면 null을 반환한다.
    private String resolveClientMessageId(Message<byte[]> clientMessage) {
        if (clientMessage == null || clientMessage.getPayload().length == 0) {
            return null;
        }

        try {
            JsonNode body = objectMapper.readTree(clientMessage.getPayload());
            if (body == null) {
                return null;
            }
            JsonNode clientMessageId = body.get("clientMessageId");
            return clientMessageId == null || clientMessageId.isNull()
                    ? null
                    : clientMessageId.asString();
        } catch (JacksonException e) {
            return null;
        }
    }

    private byte[] toPayload(StompErrorResponse response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (JacksonException e) {
            // 오류 직렬화 자체가 실패해도 클라이언트가 연결 종료 원인을 확인할 수 있어야 한다.
            return "{\"code\":\"COMMON500_1\",\"message\":\"예기치 않은 서버 에러가 발생했습니다.\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record StompErrorResponse(
            String code,
            String message,
            String clientMessageId
    ) {
    }
}
