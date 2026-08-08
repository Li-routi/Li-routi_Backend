package com.lirouti.domain.chat.dto.request;

import com.lirouti.domain.chat.enums.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ChatReqDTO {
    private ChatReqDTO() {
    }

    public record SendMessage(
            @NotBlank(message = "클라이언트 메시지 ID는 필수입니다.")
            @Size(max = 100, message = "클라이언트 메시지 ID는 100자 이하여야 합니다.")
            String clientMessageId,

            @NotNull(message = "메시지 타입은 필수입니다.")
            ChatMessageType type,

            @Size(max = 2_000, message = "텍스트 메시지는 2000자 이하여야 합니다.")
            String content,

            @Size(max = 100, message = "이모티콘 코드는 100자 이하여야 합니다.")
            String emoticonCode
    ) {
    }

    public record UpdateRead(
            @NotNull(message = "마지막 읽은 메시지 ID는 필수입니다.")
            Long lastReadMessageId
    ) {
    }
}
