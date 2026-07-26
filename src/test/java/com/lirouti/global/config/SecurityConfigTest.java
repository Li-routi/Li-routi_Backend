package com.lirouti.global.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lirouti.domain.auth.controller.AuthController;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.service.AuthService;
import com.lirouti.domain.member.controller.MemberController;
import com.lirouti.domain.member.service.command.MemberCommandService;
import com.lirouti.global.auth.filter.JwtAuthFilter;
import com.lirouti.global.auth.filter.JwtExceptionFilter;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest({AuthController.class, MemberController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtExceptionFilter.class})
@DisplayName("SecurityConfig HTTP 접근 제어 테스트")
class SecurityConfigTest {
    private static final String ACCESS_TOKEN = "access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MemberCommandService memberCommandService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RedisUtil redisUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("인증 없이 공개 인증 API에 접근할 수 있다")
    void publicAuthApi_Anonymous_AllowsAccess() throws Exception {
        // given
        AuthResDTO.GoogleNonce response = AuthResDTO.GoogleNonce.builder()
                .nonce("nonce")
                .build();
        when(authService.issueGoogleNonce()).thenReturn(response);

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/google/nonce"));

        // then
        result.andExpect(status().isOk());
        verify(authService).issueGoogleNonce();
    }

    @Test
    @DisplayName("인증 없이 회원 API에 접근하면 요청을 거부한다")
    void memberApi_Anonymous_DeniesAccess() throws Exception {
        // given
        String endpoint = "/api/members/logout";

        // when
        ResultActions result = mockMvc.perform(post(endpoint));

        // then
        result.andExpect(status().isForbidden());
        verifyNoInteractions(memberCommandService);
    }

    @Test
    @DisplayName("인증 사용자는 회원 API에 접근할 수 있다")
    void memberApi_AuthenticatedUser_AllowsAccess() throws Exception {
        // given
        Claims claims = mock(Claims.class);
        when(jwtUtil.getClaims(ACCESS_TOKEN)).thenReturn(claims);
        when(claims.get("category", String.class)).thenReturn("access");
        when(redisUtil.isBlackList(ACCESS_TOKEN)).thenReturn(false);

        // when
        ResultActions result = mockMvc.perform(post("/api/members/logout")
                .with(user("member").roles("USER"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN));

        // then
        result.andExpect(status().isOk());
        verify(memberCommandService).logout(ACCESS_TOKEN);
    }
}
