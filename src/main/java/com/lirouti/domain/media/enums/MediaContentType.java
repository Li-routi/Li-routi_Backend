package com.lirouti.domain.media.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업로드를 허용하는 미디어 형식. 허용 목록(allowlist)으로 관리하며, 목록에 없는 형식은 거부한다.
 * 각 형식은 카테고리를 가진다 — 용량 한도와 용도별 허용 여부가 카테고리 단위로 결정된다.
 *
 * <p>영상 확장 시: 아래에 형식을 추가하기만 하면 된다. 예)
 * <pre>MP4("video/mp4", "mp4", MediaCategory.VIDEO)</pre>
 * 그러면 자동으로 카테고리가 VIDEO로 분류되고, 해당 용도가 VIDEO를 허용하는지·용량이 영상 한도를
 * 넘지 않는지 검증 로직이 그대로 적용된다.
 */
@Getter
@RequiredArgsConstructor
public enum MediaContentType {
    JPEG("image/jpeg", "jpg", MediaCategory.IMAGE),
    PNG("image/png", "png", MediaCategory.IMAGE),
    WEBP("image/webp", "webp", MediaCategory.IMAGE);
    // TODO(영상): 영상 인증을 켤 때 여기에 형식 추가 → MP4("video/mp4", "mp4", MediaCategory.VIDEO), ...

    private final String mimeType;
    private final String extension;
    private final MediaCategory category;

    public static Optional<MediaContentType> from(String mimeType) {
        if (mimeType == null) {
            return Optional.empty();
        }
        // Locale.ROOT로 고정한다. 기본 로케일(예: 터키어)에서는 "I"가 "ı"로 변환되어
        // 대문자 MIME이 잘못 매칭될 수 있다.
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equals(normalized))
                .findFirst();
    }
}
