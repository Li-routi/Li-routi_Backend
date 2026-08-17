package com.lirouti.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import com.lirouti.global.apiPayload.ApiErrorResponseWriter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 권한 부족 핸들러의 최종 HTTP 응답 계약을 직접 검증한다.
 *
 * <p>관리자 역할 matcher의 진입 여부는 {@code SecurityConfigTest}가 맡는다. 여기서는
 * {@link ApiErrorResponseWriter}를 실제로 사용해 클라이언트가 받는 403 JSON을 고정한다.
 */
@DisplayName("권한 부족 응답 테스트")
class AccessDeniedHandlerImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AccessDeniedHandlerImpl handler;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new AccessDeniedHandlerImpl(new ApiErrorResponseWriter(objectMapper));
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("403과 ApiResponse 본문을 쓴다 — 빈 응답이 나가면 원인을 알 수 없다")
    void handle_WritesForbiddenWithApiResponseBody() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/challenges");

        // when
        handler.handle(request, response, new AccessDeniedException("denied"));

        // then
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertAll(
                () -> assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value()),
                // 인코딩까지 함께 지정하므로 "application/json;charset=UTF-8" 로 나간다.
                // 한글 메시지가 깨지지 않으려면 charset 이 붙어야 해서 접두 비교로 둔다.
                () -> assertThat(response.getContentType())
                        .startsWith(MediaType.APPLICATION_JSON_VALUE),
                () -> assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8"),
                () -> assertThat(body.get("isSuccess").asBoolean()).isFalse(),
                () -> assertThat(body.get("code").asString()).isEqualTo("AUTH403_1"),
                () -> assertThat(body.get("message").asString()).isNotBlank()
        );
    }

    @Test
    @DisplayName("이미 응답이 나간 뒤에는 쓰지 않는다 — 다시 쓰면 원래 오류가 예외에 묻힌다")
    void handle_ResponseAlreadyCommitted_DoesNotWrite() throws Exception {
        // given: 앞선 단계가 무언가를 내보내 응답이 커밋된 상태
        response.setStatus(HttpStatus.OK.value());
        response.getWriter().write("already sent");
        response.flushBuffer();

        // when
        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        // then: 상태도 본문도 덮어쓰지 않는다
        assertAll(
                () -> assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value()),
                () -> assertThat(response.getContentAsString()).isEqualTo("already sent")
        );
    }
}
