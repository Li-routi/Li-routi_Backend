package com.lirouti.domain.chat.dto.request;

import com.lirouti.domain.chat.enums.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
            String emoticonCode,

            @Positive(message = "답장할 메시지 ID는 양수여야 합니다.")
            Long replyToMessageId
    ) {
        public SendMessage(
                String clientMessageId,
                ChatMessageType type,
                String content,
                String emoticonCode
        ) {
            this(clientMessageId, type, content, emoticonCode, null);
        }
    }

    public record UpdateRead(
            @NotNull(message = "마지막 읽은 메시지 ID는 필수입니다.")
            Long lastReadMessageId
    ) {
    }

    public record RegisterEmoticon(
            @NotBlank(message = "이모티콘 코드는 필수입니다.")
            @Pattern(
                    regexp = "^[A-Z][A-Z0-9_]{0,99}$",
                    message = "이모티콘 코드는 대문자 영문으로 시작하고 대문자 영문, 숫자, 밑줄만 사용할 수 있습니다."
            )
            String code,

            @NotBlank(message = "미디어 형식은 필수입니다.")
            @Size(max = 30, message = "미디어 형식은 30자 이하여야 합니다.")
            String contentType,

            @NotNull(message = "표시 순서는 필수입니다.")
            @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
            Integer displayOrder
    ) {
    }

    public record UpdateEmoticonStatus(
            @NotNull(message = "활성 상태는 필수입니다.")
            Boolean active
    ) {
    }
}
