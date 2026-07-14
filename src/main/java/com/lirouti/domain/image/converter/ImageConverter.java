package com.lirouti.domain.image.converter;

import java.time.LocalDateTime;

import com.lirouti.domain.image.dto.response.ImageResDTO;

public final class ImageConverter {
    private ImageConverter() {
    }

    /**
     * 서명 URL 발급 결과를 응답으로 조립한다.
     * publicBaseUrl이 비어 있으면 imageUrl은 null로 두고 key만 내려준다.
     */
    public static ImageResDTO.PresignedUrl toPresignedUrl(
            String uploadUrl,
            String imageKey,
            String publicBaseUrl,
            LocalDateTime expiresAt
    ) {
        return ImageResDTO.PresignedUrl.builder()
                .uploadUrl(uploadUrl)
                .imageKey(imageKey)
                .imageUrl(toImageUrl(publicBaseUrl, imageKey))
                .expiresAt(expiresAt)
                .build();
    }

    private static String toImageUrl(String publicBaseUrl, String imageKey) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/" + imageKey;
    }
}
