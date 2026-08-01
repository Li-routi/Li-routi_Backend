package com.lirouti.global.apiPayload;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.lirouti.global.apiPayload.code.BaseErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 실패 응답을 {@link ApiResponse} 형태로 직접 써 내려보낸다.
 *
 * <p><b>컨트롤러에 도달하지 못한 요청을 위한 것이다.</b> 컨트롤러 안에서 난 예외는
 * {@code @RestControllerAdvice} 가 받아 같은 형태로 바꾸지만, 필터·시큐리티 계층에서 끝나는
 * 요청은 거기까지 가지 못한다. 그 구간이 응답을 직접 써야 하는데, 각자 쓰면 형식이 갈린다.
 *
 * <p>실제로 갈라져 있었다. 시큐리티가 미인증을 <b>본문 0바이트 403</b> 으로 내보내
 * 클라이언트가 JSON 파싱에 실패했고, 코드도 메시지도 없어 왜 막혔는지 알 수 없었다.
 * 형식을 한 곳에 두는 이유가 이것이다.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * 상태 코드와 본문을 함께 쓴다.
     *
     * <p>이미 커밋된 응답에는 쓰지 않는다. 앞선 단계가 무언가를 내보낸 뒤라면 여기서 다시 쓸 때
     * {@code IllegalStateException} 이 나고, 그러면 원래 알리려던 오류 대신 그 예외가 남는다.
     */
    public void write(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.onFailure(errorCode)));
    }
}
