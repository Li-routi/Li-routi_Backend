package com.lirouti.domain.media.dto.request;

import com.lirouti.domain.media.enums.MediaPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class MediaReqDTO {
    private MediaReqDTO() {
    }

    public record PresignedUrl(
            @NotNull(message = "미디어 용도는 필수입니다.")
            MediaPurpose purpose,

            @NotBlank(message = "미디어 형식(Content-Type)은 필수입니다.")
            String contentType,

            @NotNull(message = "파일 크기는 필수입니다.")
            @Positive(message = "파일 크기는 0보다 커야 합니다.")
            Long contentLength
    ) {
    }
}
