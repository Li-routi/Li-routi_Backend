package com.lirouti.domain.image.dto.request;

import com.lirouti.domain.image.enums.ImagePurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class ImageReqDTO {
    private ImageReqDTO() {
    }

    public record PresignedUrl(
            @NotNull(message = "이미지 용도는 필수입니다.")
            ImagePurpose purpose,

            @NotBlank(message = "이미지 형식(Content-Type)은 필수입니다.")
            String contentType,

            @NotNull(message = "파일 크기는 필수입니다.")
            @Positive(message = "파일 크기는 0보다 커야 합니다.")
            Long contentLength
    ) {
    }
}
