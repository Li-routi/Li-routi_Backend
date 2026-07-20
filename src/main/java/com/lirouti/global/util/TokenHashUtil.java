package com.lirouti.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class TokenHashUtil {
    private TokenHashUtil() {
    }

    public static String hash(String value) {
        try {
            // SHA-256 해시를 생성하고 Base64 URL-safe 인코딩을 적용
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest); // URL-safe Base64 인코딩
        } catch (NoSuchAlgorithmException e) { // SHA-256 알고리즘이 지원되지 않는 경우
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
