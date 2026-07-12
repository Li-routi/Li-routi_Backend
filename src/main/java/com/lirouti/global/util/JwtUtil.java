package com.lirouti.global.util;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtUtil {
    private final SecretKey secretKey;
    private final Duration accessExpiration;
    private final Duration refreshExpiration;
    private final Duration devExpiration;

    public JwtUtil(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = Duration.ofMillis(jwtProperties.getAccessToken().getExpirationTime());
        this.refreshExpiration = Duration.ofMillis(jwtProperties.getRefreshToken().getExpirationTime());
        this.devExpiration = Duration.ofMillis(jwtProperties.getDevToken().getExpirationTime());
    }

    // 토큰 생성
    private String createToken(Long memberId, Duration expiration, Role role, String category) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + expiration.toMillis());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(memberId.toString())
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .claim("role", role)
                .claim("category", category)
                .compact();
    }

    // 토큰의 유효성 검증
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("JWT 검증에 실패했습니다. exceptionType={}", e.getClass().getSimpleName());
            return false;
        }
    }

    // 유효 시간 추출
    public Long getExpirationTime(String token) {
        try {
            Claims claims = getClaims(token);
            Date expiration = claims.getExpiration();
            long now = new Date().getTime();

            // 현재 시간과의 차이를 반환
            return expiration.getTime() - now;
        } catch (Exception e) {
            return 0L;
        }
    }

    // jwt 파싱 및 클레임 추출
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims getRefreshClaims(String token) {
        try {
            return getClaims(token);
        } catch (ExpiredJwtException e) {
            log.warn("만료된 Refresh Token 파싱에 실패했습니다.");
            throw new AuthException(AuthErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Refresh Token 파싱에 실패했습니다. exceptionType={}", e.getClass().getSimpleName());
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * 로그아웃 전용: 토큰이 만료되었더라도 파싱하여 Claims를 강제로 반환
     * 단, 서명(Signature)이 다르거나 손상된 토큰이면 예외를 던짐
     */
    public Claims getClaimsForLogout(String token) {
        try {
            return getClaims(token);
        } catch (ExpiredJwtException e) {
            // 토큰이 만료되었을 뿐, 서명은 유효하므로 안에 들어있는 Claims를 꺼내서 반환
            return e.getClaims();
        } catch (Exception e) {
            // 서명이 조작되었거나 형식이 깨진 경우는 로그아웃조차 시켜주지 않음
            log.warn("로그아웃 JWT 파싱에 실패했습니다. exceptionType={}", e.getClass().getSimpleName());
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    // 액세스 토큰 생성
    public String createAccessToken(Long memberId, Role role) {
        return createToken(memberId, accessExpiration, role, "access");
    }

    // 리프레쉬 토큰 생성
    public String createRefreshToken(Long memberId) {
        return createToken(memberId, refreshExpiration, Role.ROLE_USER, "refresh");
    }

    // 개발자 전용 임시 토큰 생성
    public String createDevToken(Long memberId) {
        return createToken(memberId, devExpiration, Role.ROLE_USER, "access");
    }
}
