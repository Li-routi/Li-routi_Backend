package com.lirouti.domain.member.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.exception.code.success.MemberSuccessCode;
import com.lirouti.domain.member.service.command.MemberCommandService;
import com.lirouti.global.auth.filter.JwtAuthFilter;
import com.lirouti.global.auth.filter.JwtExceptionFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MemberController HTTP 계약 테스트")
class MemberControllerTest {
    private static final String ACCESS_TOKEN = "access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberCommandService memberCommandService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @MockitoBean
    private JwtExceptionFilter jwtExceptionFilter;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("유효한 Bearer 토큰으로 로그아웃하면 성공 응답을 반환한다")
    void logout_ValidBearerToken_ReturnsSuccessResponse() throws Exception {
        // given
        String authorization = "Bearer " + ACCESS_TOKEN;

        // when
        ResultActions result = mockMvc.perform(post("/api/members/logout")
                .header(HttpHeaders.AUTHORIZATION, authorization));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(MemberSuccessCode.MEMBER_LOGOUT_SUCCESS.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(MemberSuccessCode.MEMBER_LOGOUT_SUCCESS.getMessage()));
        verify(memberCommandService).logout(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("Authorization 헤더가 Bearer 형식이 아니면 토큰 오류 응답을 반환한다")
    void logout_InvalidAuthorizationHeader_ReturnsTokenInvalid() throws Exception {
        // given
        String authorization = "Invalid token";

        // when
        ResultActions result = mockMvc.perform(post("/api/members/logout")
                .header(HttpHeaders.AUTHORIZATION, authorization));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.TOKEN_INVALID.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.TOKEN_INVALID.getMessage()));
        verifyNoInteractions(memberCommandService);
    }

    @Test
    @DisplayName("Bearer 뒤에 토큰이 없으면 토큰 오류 응답을 반환한다")
    void logout_BlankBearerToken_ReturnsTokenInvalid() throws Exception {
        // given
        String authorization = "Bearer   ";

        // when
        ResultActions result = mockMvc.perform(post("/api/members/logout")
                .header(HttpHeaders.AUTHORIZATION, authorization));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.TOKEN_INVALID.getCode()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.TOKEN_INVALID.getMessage()));
        verifyNoInteractions(memberCommandService);
    }
}
