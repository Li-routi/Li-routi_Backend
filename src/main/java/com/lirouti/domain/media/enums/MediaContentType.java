package com.lirouti.domain.media.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업로드를 허용하는 미디어 형식. 허용 목록(allowlist)으로 관리하며, 목록에 없는 형식은 거부한다.
 * 각 형식은 카테고리를 가진다 — 용량 한도와 용도별 허용 여부가 카테고리 단위로 결정된다.
 *
 * 영상 확장 시: 아래에 형식을 추가하기만 하면 된다.
 * 예) MP4("video/mp4", "mp4", MediaCategory.VIDEO)
 * 그러면 자동으로 카테고리가 VIDEO로 분류되고, 해당 용도가 VIDEO를 허용하는지·용량이 영상 한도를
 * 넘지 않는지 검증 로직이 그대로 적용된다.
 */
@Getter
@RequiredArgsConstructor
public enum MediaContentType {
    // 세 번째 인자는 "앞부분 바이트가 이 형식인가"를 판정한다(#22).
    // 형식마다 시그니처 위치가 달라 단순 prefix 비교로는 부족하다(WEBP 참고).
    JPEG("image/jpeg", "jpg", MediaCategory.IMAGE,
            head -> startsWith(head, 0xFF, 0xD8, 0xFF)),
    PNG("image/png", "png", MediaCategory.IMAGE,
            head -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
    // WEBP는 RIFF 컨테이너다. 0~3이 "RIFF", 4~7이 파일 크기, 8~11이 "WEBP"라
    // 두 구간을 따로 봐야 한다.
    WEBP("image/webp", "webp", MediaCategory.IMAGE,
            head -> startsWith(head, 0x52, 0x49, 0x46, 0x46)
                    && matchesAt(head, 8, 0x57, 0x45, 0x42, 0x50));
    // TODO(영상): 영상 인증을 켤 때 여기에 형식 추가 → MP4(..., head -> matchesAt(head, 4, 0x66,0x74,0x79,0x70))
    //  ftyp 박스가 오프셋 4에 오므로 위 matchesAt을 그대로 쓸 수 있다.

    /** 시그니처 판정에 필요한 최대 바이트 수. 이만큼만 읽으면 모든 형식을 구분할 수 있다. */
    public static final int SIGNATURE_LENGTH = 12;

    private final String mimeType;
    private final String extension;
    private final MediaCategory category;
    private final Predicate<byte[]> signatureMatcher;

    /** 앞부분 바이트가 이 형식의 시그니처와 맞는지. 길이가 모자라면 false다. */
    public boolean matchesSignature(byte[] head) {
        return head != null && signatureMatcher.test(head);
    }

    private static boolean startsWith(byte[] head, int... expected) {
        return matchesAt(head, 0, expected);
    }

    private static boolean matchesAt(byte[] head, int offset, int... expected) {
        if (head == null || head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

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
