package com.lirouti.domain.media.converter;

import com.lirouti.domain.media.dto.response.MediaResDTO;

import java.time.Instant;

public final class MediaConverter {
    private MediaConverter() {
    }

    /**
     * 서명 URL 발급 결과를 응답으로 조립한다.
     * 공개 주소는 여기서 주지 않는다. 챌린지 인증은 심사를 통과해 승격된 뒤에야 공개 주소가
     * 생기므로, 발급 시점에는 줄 수 있는 값이 없다. 인증 저장 응답의 imageUrl 이 그 자리를 맡는다.
     */
    public static MediaResDTO.PresignedUrl toPresignedUrl(
            String uploadUrl,
            String mediaKey,
            String contentType,
            long contentLength,
            Instant expiresAt
    ) {
        return MediaResDTO.PresignedUrl.builder()
                .uploadUrl(uploadUrl)
                .mediaKey(mediaKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .expiresAt(expiresAt)
                .build();
    }
}
