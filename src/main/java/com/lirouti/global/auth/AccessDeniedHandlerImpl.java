package com.lirouti.global.auth;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.lirouti.global.apiPayload.ApiErrorResponseWriter;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증은 됐지만 권한이 모자란 요청의 응답.
 *
 * <p>{@link AuthenticationEntryPointImpl} 와 짝이다. 둘을 가르는 기준은 <b>토큰이 있었는가</b>다 —
 * 없으면 401(로그인 필요), 있는데 권한이 모자라면 403(요청 거부). 하나만 등록하면 나머지 경우가
 * 스프링 기본 동작(본문 0바이트)으로 새므로 항상 함께 둔다.
 *
 * <p><b>지금은 이 경로에 도달하는 요청이 없다.</b> 시큐리티 설정이 역할을 구분하지 않고
 * {@code anyRequest().authenticated()} 만 걸고 있어, 인증만 통과하면 모두 허용된다.
 * 그럼에도 등록하는 이유는 <b>관리자 전용 경로처럼 역할 검사가 생기는 순간 이것이 없으면
 * 그때부터 빈 403 이 나가기 때문</b>이다. 그 시점에 원인을 찾기는 어렵다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final ApiErrorResponseWriter responseWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        // 인증을 통과하고도 막힌 것이라 정상 흐름이 아니다. 미인증(debug)과 달리 warn 으로 남긴다.
        log.warn("권한이 없는 요청을 거부했습니다. method={}, uri={}",
                request.getMethod(), request.getRequestURI());
        responseWriter.write(response, GeneralErrorCode.FORBIDDEN);
    }
}
