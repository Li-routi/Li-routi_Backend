package com.lirouti.domain.media.converter;

import java.time.Instant;

import com.lirouti.domain.media.dto.response.MediaResDTO;

public final class MediaConverter {
    private MediaConverter() {
    }

    /**
     * 서명 URL 발급 결과를 응답으로 조립한다.
     * mediaUrl은 MediaService가 공개 주소로 조립해 넘겨준다.
     */
    public static MediaResDTO.PresignedUrl toPresignedUrl(
            String uploadUrl,
            String mediaKey,
            String mediaUrl,
            String contentType,
            long contentLength,
            Instant expiresAt
    ) {
        return MediaResDTO.PresignedUrl.builder()
                .uploadUrl(uploadUrl)
                .mediaKey(mediaKey)
                .mediaUrl(mediaUrl)
                .contentType(contentType)
                .contentLength(contentLength)
                .expiresAt(expiresAt)
                .build();
    }
}
