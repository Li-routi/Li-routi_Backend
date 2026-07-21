package com.lirouti.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.properties.JwtProperties;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService 테스트")
class TokenServiceTest {
    private static final long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private Member member;
    @Mock
    private Claims claims;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.getAccessToken().setExpirationTime(Duration.ofHours(1).toMillis());
        jwtProperties.getRefreshToken().setExpirationTime(Duration.ofDays(14).toMillis());
        tokenService = new TokenService(jwtUtil, redisUtil, memberRepository, jwtProperties);
    }

    @Test
    @DisplayName("토큰 발급 시 refresh token 해시를 저장하고 응답을 반환한다")
    void issueTokens_StoresRefreshTokenHashAndReturnsTokenResponse() throws Exception {
        // given
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getRole()).thenReturn(Role.ROLE_USER);
        when(member.isOnboardingCompleted()).thenReturn(false);
        when(jwtUtil.createAccessToken(MEMBER_ID, Role.ROLE_USER)).thenReturn(ACCESS_TOKEN);
        when(jwtUtil.createRefreshToken(MEMBER_ID)).thenReturn(REFRESH_TOKEN);

        // when
        AuthResDTO.Token result = tokenService.issueTokens(member);

        // then
        verify(redisUtil).set(
                "auth:refresh:" + MEMBER_ID,
                sha256(REFRESH_TOKEN),
                Duration.ofDays(14)
        );
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(result.accessTokenExpiresIn()).isEqualTo(3600L);
        assertThat(result.onboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("유효한 refresh token이면 새 토큰 쌍을 재발급한다")
    void reissue_ValidRefreshToken_ReturnsNewTokenPair() throws Exception {
        // given
        when(jwtUtil.getRefreshClaims(REFRESH_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(String.valueOf(MEMBER_ID));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getRole()).thenReturn(Role.ROLE_ADMIN);
        when(member.isActiveMember()).thenReturn(true);
        when(member.isOnboardingCompleted()).thenReturn(true);
        when(jwtUtil.createAccessToken(MEMBER_ID, Role.ROLE_ADMIN)).thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken(MEMBER_ID)).thenReturn("new-refresh-token");
        when(redisUtil.compareAndSet(
                "auth:refresh:" + MEMBER_ID,
                sha256(REFRESH_TOKEN),
                sha256("new-refresh-token"),
                Duration.ofDays(14)
        )).thenReturn(true);

        // when
        AuthResDTO.Token result = tokenService.reissue(REFRESH_TOKEN);

        // then
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(3600L);
        assertThat(result.onboardingCompleted()).isTrue();
    }

    @Test
    @DisplayName("access token을 제출하면 TOKEN_INVALID를 던진다")
    void reissue_AccessTokenCategory_ThrowsTokenInvalid() {
        // given
        when(jwtUtil.getRefreshClaims(REFRESH_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("access");

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("subject가 숫자가 아니면 TOKEN_INVALID를 던진다")
    void reissue_InvalidSubject_ThrowsTokenInvalid() {
        // given
        when(jwtUtil.getRefreshClaims(REFRESH_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn("not-a-number");

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("Redis compare-and-set에 실패하면 REFRESH_TOKEN_INVALID를 던진다")
    void reissue_RedisCompareAndSetFailed_ThrowsRefreshTokenInvalid() throws Exception {
        // given
        when(jwtUtil.getRefreshClaims(REFRESH_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(String.valueOf(MEMBER_ID));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getRole()).thenReturn(Role.ROLE_USER);
        when(member.isActiveMember()).thenReturn(true);
        when(jwtUtil.createAccessToken(MEMBER_ID, Role.ROLE_USER)).thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken(MEMBER_ID)).thenReturn("new-refresh-token");
        when(redisUtil.compareAndSet(
                "auth:refresh:" + MEMBER_ID,
                sha256(REFRESH_TOKEN),
                sha256("new-refresh-token"),
                Duration.ofDays(14)
        )).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
        verify(redisUtil).delete("auth:refresh:" + MEMBER_ID);
    }

    @Test
    @DisplayName("compare-and-set에 성공하면 refresh 세션을 삭제하지 않는다")
    void reissue_RedisCompareAndSetSucceeded_DoesNotDeleteSession() throws Exception {
        // given
        when(jwtUtil.getRefreshClaims(REFRESH_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(String.valueOf(MEMBER_ID));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getRole()).thenReturn(Role.ROLE_USER);
        when(member.isActiveMember()).thenReturn(true);
        when(jwtUtil.createAccessToken(MEMBER_ID, Role.ROLE_USER)).thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken(MEMBER_ID)).thenReturn("new-refresh-token");
        when(redisUtil.compareAndSet(
                "auth:refresh:" + MEMBER_ID,
                sha256(REFRESH_TOKEN),
                sha256("new-refresh-token"),
                Duration.ofDays(14)
        )).thenReturn(true);

        // when
        tokenService.reissue(REFRESH_TOKEN);

        // then
        verify(redisUtil, never()).delete("auth:refresh:" + MEMBER_ID);
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER_NOT_FOUND를 던진다")
    void reissue_MemberNotFound_ThrowsMemberNotFound() {
        // given
        when(jwtUtil.getRefreshClaims(REFRESH_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(String.valueOf(MEMBER_ID));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(REFRESH_TOKEN))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 회원이면 WITHDRAWN_MEMBER를 던진다")
    void reissue_InactiveMember_ThrowsWithdrawnMember() {
        // given
        when(jwtUtil.getRefreshClaims(REFRESH_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(String.valueOf(MEMBER_ID));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.isActiveMember()).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> tokenService.reissue(REFRESH_TOKEN))
                .isInstanceOf(MemberException.class)
                .extracting("code")
                .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
