package com.lirouti.global.auth.filter;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtExceptionFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            handleException(response, e);
        }
    }

    private void handleException(HttpServletResponse response, Exception e) throws IOException {
        BaseErrorCode errorCode = switch (e) {
            case ExpiredJwtException ex -> {
                log.warn("JWT 토큰이 만료되었습니다.");
                yield AuthErrorCode.TOKEN_EXPIRED;
            }
            case MalformedJwtException ex -> {
                log.warn("JWT 토큰 형식이 유효하지 않습니다.");
                yield AuthErrorCode.TOKEN_INVALID;
            }
            case JwtException ex -> {
                log.warn("JWT 처리 중 예외가 발생했습니다. exceptionType={}",
                        ex.getClass().getSimpleName());
                yield AuthErrorCode.TOKEN_INVALID;
            }
            case AuthException ex -> {
                log.warn("인증 예외 발생: {}", ex.getMessage());
                yield ex.getCode();
            }
            case GeneralException ex -> {
                log.warn("비즈니스 로직 예외 발생: {}", ex.getMessage());
                yield ex.getCode();
            }
            default -> {
                log.error("필터 체인에서 알 수 없는 예외가 발생했습니다: ", e);
                yield GeneralErrorCode.INTERNAL_SERVER_ERROR;
            }
        };

        setErrorResponse(response, errorCode);
    }

    private void setErrorResponse(HttpServletResponse response, BaseErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.onFailure(code);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
