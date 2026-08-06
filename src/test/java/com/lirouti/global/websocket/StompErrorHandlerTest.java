package com.lirouti.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("StompErrorHandler 테스트")
class StompErrorHandlerTest {
    private ObjectMapper objectMapper;
    private StompErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        errorHandler = new StompErrorHandler(objectMapper);
    }

    @Test
    @DisplayName("도메인 예외의 코드와 clientMessageId를 ERROR payload에 보존한다")
    void handleClientMessageProcessingError_DomainException_PreservesErrorContract() throws Exception {
        // given
        Message<byte[]> clientMessage = stompMessage(
                StompCommand.SEND,
                "{\"clientMessageId\":\"client-1\"}",
                "receipt-1"
        );

        // when
        Message<byte[]> errorMessage = errorHandler.handleClientMessageProcessingError(
                clientMessage,
                new ChatException(ChatErrorCode.MESSAGE_CONTENT_INVALID)
        );

        // then
        assertThat(errorHeader(errorMessage).getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(errorHeader(errorMessage).getReceiptId()).isEqualTo("receipt-1");
        JsonNode payload = errorPayload(errorMessage);
        assertThat(payload.get("code").asText())
                .isEqualTo(ChatErrorCode.MESSAGE_CONTENT_INVALID.getCode());
        assertThat(payload.get("message").asText())
                .isEqualTo(ChatErrorCode.MESSAGE_CONTENT_INVALID.getMessage());
        assertThat(payload.get("clientMessageId").asText()).isEqualTo("client-1");
    }

    @Test
    @DisplayName("STOMP 계층 오류는 공통 잘못된 요청 코드와 원인 메시지를 사용한다")
    void handleClientMessageProcessingError_MessagingException_UsesBadRequestCode() throws Exception {
        // given
        Message<byte[]> clientMessage = stompMessage(
                StompCommand.SUBSCRIBE,
                "{\"clientMessageId\":\"client-2\"}",
                null
        );
        MessagingException exception = new MessagingException(clientMessage, "잘못된 destination입니다.");

        // when
        Message<byte[]> errorMessage = errorHandler.handleClientMessageProcessingError(
                clientMessage,
                exception
        );

        // then
        JsonNode payload = errorPayload(errorMessage);
        assertThat(payload.get("code").asText()).isEqualTo(GeneralErrorCode.BAD_REQUEST.getCode());
        assertThat(payload.get("message").asText()).isEqualTo("잘못된 destination입니다.");
        assertThat(payload.get("clientMessageId").asText()).isEqualTo("client-2");
    }

    @Test
    @DisplayName("예기치 않은 오류는 공통 서버 오류 코드로 변환한다")
    void handleClientMessageProcessingError_UnexpectedException_UsesInternalServerErrorCode() throws Exception {
        // given
        Message<byte[]> clientMessage = stompMessage(
                StompCommand.SEND,
                "{\"clientMessageId\":\"client-3\"}",
                null
        );

        // when
        Message<byte[]> errorMessage = errorHandler.handleClientMessageProcessingError(
                clientMessage,
                new IllegalStateException("internal detail")
        );

        // then
        JsonNode payload = errorPayload(errorMessage);
        assertThat(payload.get("code").asText())
                .isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(payload.get("message").asText())
                .isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(payload.get("clientMessageId").asText()).isEqualTo("client-3");
    }

    @Test
    @DisplayName("파싱할 수 없는 요청 본문은 clientMessageId 없이 오류를 반환한다")
    void handleClientMessageProcessingError_MalformedPayload_OmitsClientMessageId() throws Exception {
        // given
        Message<byte[]> clientMessage = stompMessage(StompCommand.SEND, "not-json", null);

        // when
        Message<byte[]> errorMessage = errorHandler.handleClientMessageProcessingError(
                clientMessage,
                new MessagingException(clientMessage, "요청 형식이 올바르지 않습니다.")
        );

        // then
        JsonNode payload = errorPayload(errorMessage);
        assertThat(payload.get("code").asText()).isEqualTo(GeneralErrorCode.BAD_REQUEST.getCode());
        assertThat(payload.has("clientMessageId")).isFalse();
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String payload,
            String receipt
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setReceipt(receipt);
        return MessageBuilder.createMessage(
                payload.getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders()
        );
    }

    private StompHeaderAccessor errorHeader(Message<byte[]> errorMessage) {
        return StompHeaderAccessor.wrap(errorMessage);
    }

    private JsonNode errorPayload(Message<byte[]> errorMessage) throws Exception {
        return objectMapper.readTree(errorMessage.getPayload());
    }
}
