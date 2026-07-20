package com.lirouti.domain.auth.service;

import java.time.Duration;
import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.lirouti.domain.auth.converter.AuthConverter;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.properties.JwtProperties;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import com.lirouti.global.util.TokenHashUtil;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TokenService {
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final MemberRepository memberRepository;
    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;

    public TokenService(
            JwtUtil jwtUtil,
            RedisUtil redisUtil,
            MemberRepository memberRepository,
            JwtProperties jwtProperties
    ) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.memberRepository = memberRepository;
        this.accessTokenExpiration = Duration.ofMillis(
                jwtProperties.getAccessToken().getExpirationTime());
        this.refreshTokenExpiration = Duration.ofMillis(
                jwtProperties.getRefreshToken().getExpirationTime());
    }

    public AuthResDTO.Token issueTokens(Member member) {
        String accessToken = jwtUtil.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtUtil.createRefreshToken(member.getId());

        redisUtil.set(
                getRefreshTokenKey(member.getId()),
                TokenHashUtil.hash(refreshToken),
                refreshTokenExpiration
        );
        log.debug("서비스 토큰을 발급하고 Refresh Token 해시를 저장했습니다. memberId={}", member.getId());

        return AuthConverter.toToken(
                accessToken,
                refreshToken,
                accessTokenExpiration.toSeconds(),
                member.isOnboardingCompleted()
        );
    }

    public AuthResDTO.Token reissue(String refreshToken) {
        Claims claims = jwtUtil.getRefreshClaims(refreshToken);
        validateRefreshCategory(claims);

        Long memberId = parseMemberId(claims.getSubject());
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        validateActiveMember(member);

        String newAccessToken = jwtUtil.createAccessToken(member.getId(), member.getRole());
        String newRefreshToken = jwtUtil.createRefreshToken(member.getId());
        
        // Refresh Token 해시를 Redis에 저장하고, 기존 해시와 비교하여 일치하지 않으면 예외 발생
        boolean stored = redisUtil.compareAndSet(
                getRefreshTokenKey(member.getId()),
                TokenHashUtil.hash(refreshToken),
                TokenHashUtil.hash(newRefreshToken),
                refreshTokenExpiration
        );

        if (!stored) {
            redisUtil.delete(getRefreshTokenKey(member.getId()));
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        log.info("서비스 토큰을 재발급했습니다. memberId={}", member.getId());

        return AuthConverter.toToken(
                newAccessToken,
                newRefreshToken,
                accessTokenExpiration.toSeconds(),
                member.isOnboardingCompleted()
        );
    }

    public Long logout(String accessToken) {
        Claims claims = jwtUtil.getClaimsForLogout(accessToken);
        validateAccessCategory(claims);

        Long memberId = parseMemberId(claims.getSubject());

        Date expiration = claims.getExpiration();

        // Redis에 블랙리스트 토큰으로 등록하고, 남은 유효 시간 동안 유지
        long remainingTime = expiration == null
                ? Math.max(jwtUtil.getExpirationTime(accessToken), 0L)
                : Math.max(expiration.getTime() - System.currentTimeMillis(), 0L);

        redisUtil.setBlackList(accessToken, remainingTime);
        redisUtil.delete(getRefreshTokenKey(memberId));

        return memberId;
    }

    private String getRefreshTokenKey(Long memberId) {
        return REFRESH_TOKEN_KEY_PREFIX + memberId;
    }

    private void validateRefreshCategory(Claims claims) {
        String category = claims.get("category", String.class);
        if (!"refresh".equals(category)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    private void validateAccessCategory(Claims claims) {
        String category = claims.get("category", String.class);
        if (!"access".equals(category)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    private Long parseMemberId(String memberId) {
        if (!StringUtils.hasText(memberId)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        try {
            return Long.valueOf(memberId);
        } catch (NumberFormatException e) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    // 탈퇴한 회원인지 확인
    private void validateActiveMember(Member member) {
        if (!Boolean.TRUE.equals(member.getIsActive()) || member.getDeletedAt() != null) {
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }
    }

}
