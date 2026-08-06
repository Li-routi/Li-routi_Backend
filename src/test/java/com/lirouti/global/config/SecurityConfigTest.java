package com.lirouti.global.config;

import com.lirouti.domain.auth.controller.AuthController;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.service.AuthService;
import com.lirouti.domain.member.controller.MemberController;
import com.lirouti.domain.member.service.MemberProfileService;
import com.lirouti.domain.member.service.command.MemberCommandService;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.apiPayload.ApiErrorResponseWriter;
import com.lirouti.global.auth.AccessDeniedHandlerImpl;
import com.lirouti.global.auth.AuthenticationEntryPointImpl;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, MemberController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtExceptionFilter.class,
        AuthenticationEntryPointImpl.class, AccessDeniedHandlerImpl.class,
        ApiErrorResponseWriter.class})
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

    @MockitoBean
    private MemberQueryService memberQueryService;

    @MockitoBean
    private MemberProfileService memberProfileService;

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
    @DisplayName("인증 없이 회원 API에 접근하면 401과 ApiResponse 본문을 준다")
    void memberApi_Anonymous_Returns401WithBody() throws Exception {
        // given
        String endpoint = "/api/members/logout";

        // when
        ResultActions result = mockMvc.perform(post(endpoint));

        // then: 상태 코드와 본문 둘 다 계약이다.
        // 403이면 클라이언트의 "401이면 재발급" 분기가 걸리지 않고,
        // 본문이 비면 코드도 메시지도 없어 왜 막혔는지 알 수 없다.
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401_1"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(memberCommandService);
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 404다 — 주소 오타가 서버 오류로 보이면 안 된다")
    void unmappedPath_Returns404() throws Exception {
        // when: 존재하지 않는 경로. 인증을 통과시켜 시큐리티가 아니라 라우팅에서 걸리게 한다
        ResultActions result = mockMvc.perform(post("/api/v1/members/logout")
                .with(user("member").roles("USER")));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON404_1"));
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
