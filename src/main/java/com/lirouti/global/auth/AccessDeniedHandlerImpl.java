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
 * <p>{@link AuthenticationEntryPointImpl} 와 짝이다. 인증되지 않은 요청은 401 진입점으로,
 * 인증됐지만 권한 검사에서 거부된 요청은 이 403 핸들러로 온다. 하나만 등록하면 나머지 경우가
 * 스프링 기본 응답으로 새므로 항상 함께 둔다.
 *
 * <p>관리자 채팅 이모티콘 경로는 {@code ROLE_ADMIN} authority를 요구한다. 인증된 일반 회원이
 * 이 경로에 접근하면 여기서 공통 {@code AUTH403_1} JSON 응답을 받는다.
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
