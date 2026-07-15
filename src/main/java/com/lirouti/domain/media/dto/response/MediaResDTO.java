package com.lirouti.domain.media.dto.response;

import java.time.Instant;

import lombok.Builder;

public final class MediaResDTO {
    private MediaResDTO() {
    }

    /**
     * uploadUrl로 PUT 업로드한 뒤, mediaKey를 서버에 저장한다.
     * 업로드 시 이 응답의 contentType·contentLength를 그대로 PUT 요청 헤더에 실어야 서명이 유효하다.
     * 요청에 보낸 원본 문자열(예: 대소문자·공백)이 아니라, 서버가 정규화해 서명에 사용한 값을 돌려주는
     * 것이므로 반드시 이 값을 써야 한다.
     * expiresAt은 S3가 서명에 실제로 부여한 만료 시각(UTC Instant)이다.
     */
    @Builder
    public record PresignedUrl(
            String uploadUrl,
            String mediaKey,
            String mediaUrl,
            String contentType,
            long contentLength,
            Instant expiresAt
    ) {
    }
}
