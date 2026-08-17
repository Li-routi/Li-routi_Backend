package com.lirouti.global.auth;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.lirouti.global.apiPayload.ApiErrorResponseWriter;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증하지 않은 요청이 보호된 경로에 닿았을 때의 응답.
 *
 * <p>등록하지 않으면 스프링 기본 동작이 <b>본문 0바이트 403</b> 을 내보낸다. 두 가지가 틀리다 —
 * 본문이 비어 있어 클라이언트가 {@code ApiResponse} 파싱에 실패하고, 상태 코드도 인증이 안 된
 * 상황에는 <b>401</b> 이 맞다(403 은 "인증은 됐는데 권한이 없다"는 뜻이다).
 *
 * <p>상태 코드가 401 이어야 하는 실질적인 이유가 하나 더 있다. 클라이언트는 보통 <b>401 을
 * 보고 토큰 재발급</b>을 돌리는데, 403 으로 나가면 그 분기가 걸리지 않는다.
 *
 * <p><b>토큰이 잘못된 경우는 여기로 오지 않는다.</b> 만료·형식오류·블랙리스트는
 * {@link com.lirouti.global.auth.filter.JwtAuthFilter} 가 예외를 던지고
 * {@link com.lirouti.global.auth.filter.JwtExceptionFilter} 가 {@code AUTH401_*} 로 바꾼다.
 * 여기 오는 것은 <b>토큰을 아예 안 보낸</b> 요청이라 "로그인이 필요하다"가 정확한 안내다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter responseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        // 봇 스캐너가 보호된 경로를 긁는 것도 여기로 온다. 흔한 일이라 warn 으로 올리지 않는다.
        log.debug("인증 없이 보호된 경로에 접근했습니다. method={}, uri={}",
                request.getMethod(), request.getRequestURI());
        responseWriter.write(response, GeneralErrorCode.UNAUTHORIZED);
    }
}
