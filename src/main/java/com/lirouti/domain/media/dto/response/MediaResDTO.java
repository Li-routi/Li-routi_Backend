package com.lirouti.domain.media.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;

public final class MediaResDTO {
    private MediaResDTO() {
    }

    /**
     * uploadUrl로 PUT 업로드한 뒤, mediaKey를 서버에 저장한다.
     * 업로드 시 요청한 Content-Type과 Content-Length를 그대로 헤더에 실어야 서명이 유효하다.
     */
    @Builder
    public record PresignedUrl(
            String uploadUrl,
            String mediaKey,
            String mediaUrl,
            LocalDateTime expiresAt
    ) {
    }
}
