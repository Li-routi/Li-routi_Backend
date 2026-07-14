package com.lirouti.domain.auth.service;

import com.lirouti.global.util.RedisUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleNonceService {
    private static final String NONCE_KEY_PREFIX = "auth:google:nonce:";
    private static final Duration NONCE_TTL = Duration.ofMinutes(5);
    // 32바이트 길이의 nonce를 생성하고, Base64 URL-safe 인코딩 후 Redis에 저장 용도
    private static final int NONCE_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisUtil redisUtil;

    // nonce를 생성하고 Redis에 저장
    public String issueNonce() {
        byte[] nonceBytes = new byte[NONCE_BYTE_LENGTH];
        // 난수 생성
        SECURE_RANDOM.nextBytes(nonceBytes);

        // Base64 URL-safe 인코딩 후 padding 제거
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        // Redis에 nonce를 저장하고 TTL 설정
        redisUtil.set(getNonceKey(nonce), "issued", NONCE_TTL);
        log.debug("Google nonce를 발급했습니다. ttlSeconds={}", NONCE_TTL.toSeconds());
        return nonce;
    }

    // nonce를 검증하고, 재사용 방지를 위해 Redis에서 삭제
    public boolean consumeNonce(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            log.warn("Google nonce 소비 요청에 nonce가 없습니다.");
            return false;
        }
        boolean consumed = redisUtil.getAndDelete(getNonceKey(nonce)) != null;
        if (consumed) {
            log.debug("Google nonce를 소비했습니다.");
        } else {
            log.warn("발급되지 않았거나 이미 소비된 Google nonce입니다.");
        }
        return consumed;
    }

    private String getNonceKey(String nonce) {
        return NONCE_KEY_PREFIX + hash(nonce);
    }

    // SHA-256 해시를 사용하여 nonce를 안전하게 저장하려는 용도
    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
