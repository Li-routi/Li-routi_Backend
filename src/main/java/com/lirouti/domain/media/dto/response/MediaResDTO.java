package com.lirouti.domain.media.dto.response;

import java.time.Instant;

import lombok.Builder;

public final class MediaResDTO {
    private MediaResDTO() {
    }

    /**
     * uploadUrl로 PUT 업로드한 뒤, mediaKey를 서버에 저장한다.
     * 업로드 시 요청한 Content-Type과 Content-Length를 그대로 헤더에 실어야 서명이 유효하다.
     * expiresAt은 S3가 서명에 실제로 부여한 만료 시각(UTC Instant)이다.
     */
    @Builder
    public record PresignedUrl(
            String uploadUrl,
            String mediaKey,
            String mediaUrl,
            Instant expiresAt
    ) {
    }
}
