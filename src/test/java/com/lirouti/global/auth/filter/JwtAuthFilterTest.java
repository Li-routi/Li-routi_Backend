package com.lirouti.global.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter 테스트")
class JwtAuthFilterTest {
    private static final String TOKEN = "access-token";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private Claims claims;

    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        jwtAuthFilter = new JwtAuthFilter(jwtUtil, redisUtil);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtUtil.getClaims(TOKEN)).thenReturn(claims);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 access token이면 인증 객체를 세팅한다")
    void doFilterInternal_ValidToken_AuthenticatesWithoutMemberLookup() throws Exception {
        // given
        when(claims.get("category", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");

        // when
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        // then
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        assertThat(principal.getMemberId()).isEqualTo(1L);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(redisUtil).isBlackList(TOKEN);
        verifyNoMoreInteractions(redisUtil);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("refresh token이면 인증을 거부한다")
    void doFilterInternal_RefreshToken_RejectsAuthentication() {
        // given
        when(claims.get("category", String.class)).thenReturn("refresh");

        // when & then
        assertInvalidCategory();
    }

    @Test
    @DisplayName("알 수 없는 category면 인증을 거부한다")
    void doFilterInternal_UnknownCategory_RejectsAuthentication() {
        // given
        when(claims.get("category", String.class)).thenReturn("unknown");

        // when & then
        assertInvalidCategory();
    }

    @Test
    @DisplayName("category가 없으면 인증을 거부한다")
    void doFilterInternal_MissingCategory_RejectsAuthentication() {
        // given
        when(claims.get("category", String.class)).thenReturn(null);

        // when & then
        assertInvalidCategory();
    }

    private void assertInvalidCategory() {
        assertThatThrownBy(() -> jwtAuthFilter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.TOKEN_INVALID);
        verify(redisUtil, never()).isBlackList(TOKEN);
    }
}
