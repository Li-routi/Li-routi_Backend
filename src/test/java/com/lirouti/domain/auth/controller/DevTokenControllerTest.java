package com.lirouti.domain.auth.controller;

import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.auth.exception.code.success.AuthSuccessCode;
import com.lirouti.domain.auth.service.DevTokenService;
import com.lirouti.global.auth.filter.JwtAuthFilter;
import com.lirouti.global.auth.filter.JwtExceptionFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code local} 을 함께 활성화해야 컨트롤러 빈이 등록된다. 이 슬라이스는 DataSource 를 만들지
 * 않으므로, git 미추적인 {@code application-local.yaml}(개인별 DB 포트 오버라이드)이 있든 없든
 * 결과가 같다 — 로컬과 CI 가 갈리지 않는다.
 *
 * <p>다른 프로파일에서 이 엔드포인트가 없다는 것은 {@link DevTokenProfileTest}가 확인한다.
 */
@WebMvcTest(DevTokenController.class)
@ActiveProfiles({"test", "local"})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DevTokenController HTTP 계약 테스트")
class DevTokenControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DevTokenService devTokenService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;

    @MockitoBean
    private JwtExceptionFilter jwtExceptionFilter;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("토큰과 만료 시간을 성공 응답으로 내려준다")
    void issueDevToken_Success_ReturnsToken() throws Exception {
        // given
        when(devTokenService.issue(9001L)).thenReturn(
                AuthResDTO.DevToken.builder()
                        .accessToken("dev.jwt.token")
                        .accessTokenExpiresIn(1_209_600_000L)
                        .build());

        // when & then
        mockMvc.perform(post("/api/dev/token/9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(AuthSuccessCode.DEV_TOKEN_ISSUE_SUCCESS.getCode()))
                .andExpect(jsonPath("$.result.accessToken").value("dev.jwt.token"))
                .andExpect(jsonPath("$.result.accessTokenExpiresIn").value(1_209_600_000L));
    }

    @Test
    @DisplayName("없는 회원이면 404와 도메인 코드를 내려준다")
    void issueDevToken_MemberNotFound_Returns404() throws Exception {
        // given
        when(devTokenService.issue(1L))
                .thenThrow(new AuthException(AuthErrorCode.DEV_TOKEN_MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/dev/token/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.DEV_TOKEN_MEMBER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("회원 id가 숫자가 아니면 500이 아니라 400으로 막는다")
    void issueDevToken_NonNumericId_Returns400() throws Exception {
        // when & then — 경로 변수 타입 변환 실패가 전역 처리기에서 400으로 바뀌는지 본다.
        mockMvc.perform(post("/api/dev/token/abc"))
                .andExpect(status().isBadRequest());
    }
}
