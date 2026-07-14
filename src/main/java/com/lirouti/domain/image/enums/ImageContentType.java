package com.lirouti.domain.image.enums;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업로드를 허용하는 이미지 형식.
 * 허용 목록(allowlist)으로 관리한다. 목록에 없는 형식은 거부한다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageContentType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    public static Optional<ImageContentType> from(String mimeType) {
        if (mimeType == null) {
            return Optional.empty();
        }
        String normalized = mimeType.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equals(normalized))
                .findFirst();
    }
}
