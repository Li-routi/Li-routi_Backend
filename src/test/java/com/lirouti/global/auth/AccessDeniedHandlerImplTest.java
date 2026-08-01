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
 * 권한 부족 응답.
 *
 * <p><b>지금 이 핸들러에 도달하는 요청이 없다.</b> 시큐리티가 역할을 구분하지 않고
 * {@code anyRequest().authenticated()} 만 걸고 있어, 통합 테스트로는 이 경로를 만들 수 없다.
 * 그래서 핸들러를 직접 불러 확인한다.
 *
 * <p>테스트가 필요한 이유가 바로 그 "도달하지 않는다"에 있다. 관리자 전용 경로처럼 역할 검사가
 * 생기는 날 이 배선이 끊겨 있으면 <b>그때부터 본문 0바이트 403 이 나가고, 원인을 찾기 어렵다.</b>
 * 지금 고정해 두면 그 사이 누가 설정을 건드려도 여기서 걸린다.
 *
 * <p>{@link ApiErrorResponseWriter} 를 mock 하지 않고 진짜를 쓴다. 확인하려는 것이 "위임했는가"가
 * 아니라 <b>클라이언트가 실제로 받는 JSON</b> 이기 때문이다.
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
