package com.lirouti.domain.shop.service.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 장바구니를 가리키는 값.
 *
 * <p>같은 멱등 키로 <b>다른</b> 장바구니가 들어온 것을 가려내려고 쓴다.
 *
 * <p><b>계산법을 못박는 것이 요점이다.</b> "정렬한 id 의 해시" 정도로 두면 구현이 조금만 달라도
 * 같은 장바구니가 다른 값이 되어 <b>정상 재시도가 거절된다.</b>
 *
 * <pre>
 * 1. 숫자로 오름차순 정렬   (문자열 정렬이면 "10" 이 "9" 보다 앞선다)
 * 2. 십진수로 찍어 쉼표로 잇는다   "3,7,12"
 * 3. UTF-8 로 인코딩해 SHA-256 → 소문자 hex 64자
 * </pre>
 *
 * <p><b>구분자가 없으면 안 된다.</b> 이어 붙이기만 하면 {@code [1, 23]} 과 {@code [12, 3]} 이
 * 같은 입력이 된다.
 */
public final class CartFingerprint {

    private CartFingerprint() {
    }

    /**
     * 장바구니 지문.
     *
     * <p>빈 목록과 중복 id 는 여기 오기 전에 걸러진다 — 요청 DTO 의 {@code @NotEmpty} 와
     * 구매의 중복 검사다. 수량 개념이 없어 같은 아이템을 두 번 담는 것은 뜻이 없으므로,
     * 이 메서드는 <b>중복 없는 비어 있지 않은 집합</b>만 다룬다.
     */
    public static String of(List<Long> itemIds) {
        String joined = itemIds.stream()
                .sorted()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new IllegalArgumentException(
                        "빈 장바구니로는 지문을 만들 수 없습니다. 앞단에서 걸러져야 합니다."));

        return HexFormat.of().formatHex(sha256(joined));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 JDK 가 반드시 제공한다. 여기 오면 런타임이 깨진 것이다.
            throw new IllegalStateException("SHA-256 을 찾을 수 없습니다.", e);
        }
    }
}
