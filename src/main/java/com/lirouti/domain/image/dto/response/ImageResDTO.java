package com.lirouti.domain.image.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;

public final class ImageResDTO {
    private ImageResDTO() {
    }

    /**
     * uploadUrl로 PUT 업로드한 뒤, imageKey를 서버에 저장한다.
     * 업로드 시 요청한 Content-Type과 Content-Length를 그대로 헤더에 실어야 서명이 유효하다.
     */
    @Builder
    public record PresignedUrl(
            String uploadUrl,
            String imageKey,
            String imageUrl,
            LocalDateTime expiresAt
    ) {
    }
}
