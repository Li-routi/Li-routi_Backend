package com.lirouti.domain.auth.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.lirouti.domain.auth.dto.request.AuthReqDTO;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.code.success.AuthSuccessCode;
import com.lirouti.domain.auth.service.AuthService;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.auth.filter.JwtAuthFilter;
import com.lirouti.global.auth.filter.JwtExceptionFilter;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // 필터를 비활성화하여 테스트 환경에서 JWT 인증을 우회
@DisplayName("AuthController HTTP 계약 테스트")
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc; // MockMvc를 사용하여 컨트롤러의 HTTP 요청과 응답을 테스트

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @MockitoBean
    private JwtExceptionFilter jwtExceptionFilter;
    
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext; // JPA 관련 의존성을 우회

    @Test
    @DisplayName("Google nonce 발급 요청에 성공 응답을 반환한다")
    void issueGoogleNonce_Success_ReturnsSuccessResponse() throws Exception {
        // given
        AuthResDTO.GoogleNonce serviceResponse = AuthResDTO.GoogleNonce.builder()
                .nonce("nonce")
                .build();
        when(authService.issueGoogleNonce()).thenReturn(serviceResponse);

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/google/nonce"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(AuthSuccessCode.GOOGLE_NONCE_ISSUE_SUCCESS.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(AuthSuccessCode.GOOGLE_NONCE_ISSUE_SUCCESS.getMessage()))
                .andExpect(jsonPath("$.result.nonce").value("nonce"));
        verify(authService).issueGoogleNonce();
    }

    @Test
    @DisplayName("유효한 소셜 로그인 요청에 로그인 성공 응답을 반환한다")
    void socialLogin_ValidRequest_ReturnsSuccessResponse() throws Exception {
        // given
        AuthReqDTO.SocialLogin request = new AuthReqDTO.SocialLogin(
                SocialProvider.KAKAO,
                "provider-token",
                null
        );
        AuthResDTO.Token serviceResponse = AuthResDTO.Token.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenExpiresIn(3600L)
                .onboardingCompleted(false)
                .build();
        when(authService.socialLogin(request)).thenReturn(serviceResponse);

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "provider": "KAKAO",
                          "providerToken": "provider-token",
                          "nonce": null
                        }
                        """));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(AuthSuccessCode.LOGIN_SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(AuthSuccessCode.LOGIN_SUCCESS.getMessage()))
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.result.accessTokenExpiresIn").value(3600L))
                .andExpect(jsonPath("$.result.onboardingCompleted").value(false));
        verify(authService).socialLogin(request);
    }

    @Test
    @DisplayName("소셜 로그인 토큰이 공백이면 잘못된 요청 응답을 반환한다")
    void socialLogin_BlankProviderToken_ReturnsBadRequest() throws Exception {
        // given
        String requestBody = """
                {
                  "provider": "KAKAO",
                  "providerToken": " ",
                  "nonce": null
                }
                """;

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/social-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.result").value("[providerToken] 소셜 로그인 토큰은 필수입니다."));
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("유효한 토큰 재발급 요청에 재발급 성공 응답을 반환한다")
    void reissue_ValidRequest_ReturnsSuccessResponse() throws Exception {
        // given
        AuthReqDTO.Reissue request = new AuthReqDTO.Reissue("refresh-token");
        AuthResDTO.Token serviceResponse = AuthResDTO.Token.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenExpiresIn(3600L)
                .onboardingCompleted(false)
                .build();
        when(authService.reissue(request)).thenReturn(serviceResponse);

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "refreshToken": "refresh-token"
                        }
                        """));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(AuthSuccessCode.TOKEN_REFRESH_SUCCESS.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(AuthSuccessCode.TOKEN_REFRESH_SUCCESS.getMessage()))
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.refreshToken").value("refresh-token"));
        verify(authService).reissue(request);
    }

    @Test
    @DisplayName("Refresh Token이 공백이면 잘못된 요청 응답을 반환한다")
    void reissue_BlankRefreshToken_ReturnsBadRequest() throws Exception {
        // given
        String requestBody = """
                {
                  "refreshToken": " "
                }
                """;

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(GeneralErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.result").value("[refreshToken] Refresh Token은 필수입니다."));
        verifyNoInteractions(authService);
    }
}
