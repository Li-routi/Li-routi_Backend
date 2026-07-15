package com.lirouti.domain.media.converter;

import java.time.Instant;

import com.lirouti.domain.media.dto.response.MediaResDTO;

public final class MediaConverter {
    private MediaConverter() {
    }

    /**
     * 서명 URL 발급 결과를 응답으로 조립한다.
     * publicBaseUrl이 비어 있으면 mediaUrl은 null로 두고 key만 내려준다.
     */
    public static MediaResDTO.PresignedUrl toPresignedUrl(
            String uploadUrl,
            String mediaKey,
            String publicBaseUrl,
            Instant expiresAt
    ) {
        return MediaResDTO.PresignedUrl.builder()
                .uploadUrl(uploadUrl)
                .mediaKey(mediaKey)
                .mediaUrl(toMediaUrl(publicBaseUrl, mediaKey))
                .expiresAt(expiresAt)
                .build();
    }

    private static String toMediaUrl(String publicBaseUrl, String mediaKey) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/" + mediaKey;
    }
}
