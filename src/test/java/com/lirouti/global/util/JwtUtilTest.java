package com.lirouti.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtUtil 테스트")
class JwtUtilTest {
    @Test
    @DisplayName("access token 생성 시 memberId와 role이 클레임에 저장된다")
    void createAccessToken_StoresMemberIdAndRoleAsClaims() {
        // given
        JwtUtil jwtUtil = createJwtUtil(Duration.ofDays(14));

        // when
        String token = jwtUtil.createAccessToken(1L, Role.ROLE_USER);
        Claims claims = jwtUtil.getClaims(token);

        // then
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
        assertThat(claims.get("category", String.class)).isEqualTo("access");
    }

    @Test
    @DisplayName("유효한 refresh token이면 Claims를 반환한다")
    void getRefreshClaims_ValidToken_ReturnsClaims() {
        // given
        JwtUtil jwtUtil = createJwtUtil(Duration.ofDays(14));
        String token = jwtUtil.createRefreshToken(1L);

        // when
        Claims claims = jwtUtil.getRefreshClaims(token);

        // then
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("category", String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("같은 회원의 refresh token을 연속 발급해도 서로 다른 토큰을 생성한다")
    void createRefreshToken_ConsecutiveIssuance_ReturnsDistinctTokens() {
        // given
        JwtUtil jwtUtil = createJwtUtil(Duration.ofDays(14));

        // when
        String firstToken = jwtUtil.createRefreshToken(1L);
        String secondToken = jwtUtil.createRefreshToken(1L);

        // then
        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(jwtUtil.getClaims(firstToken).getId()).isNotBlank();
        assertThat(jwtUtil.getClaims(secondToken).getId()).isNotBlank();
    }

    @Test
    @DisplayName("만료된 refresh token이면 TOKEN_EXPIRED를 던진다")
    void getRefreshClaims_ExpiredToken_ThrowsTokenExpired() {
        // given
        JwtUtil jwtUtil = createJwtUtil(Duration.ofMillis(-1));
        String token = jwtUtil.createRefreshToken(1L);

        // when & then
        assertThatThrownBy(() -> jwtUtil.getRefreshClaims(token))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("손상된 토큰으로 로그아웃용 Claims 조회 시 커스텀 예외를 던진다")
    void getClaimsForLogout_InvalidToken_ThrowsAuthException() {
        // given
        JwtUtil jwtUtil = createJwtUtil(Duration.ofDays(14));

        // when & then
        assertThatThrownBy(() -> jwtUtil.getClaimsForLogout("broken-token"))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.TOKEN_INVALID);
    }

    private JwtUtil createJwtUtil(Duration refreshExpiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-must-be-at-least-32-bytes-long");
        properties.getAccessToken().setExpirationTime(Duration.ofHours(1).toMillis());
        properties.getRefreshToken().setExpirationTime(refreshExpiration.toMillis());
        properties.getDevToken().setExpirationTime(Duration.ofHours(1).toMillis());
        
        return new JwtUtil(properties);
    }
}
