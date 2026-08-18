package com.lirouti.global.auth.filter;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.lirouti.domain.achievement.service.command.MidnightAccessCommandService;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final MidnightAccessCommandService midnightAccessCommandService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            Claims claims = jwtUtil.getClaims(token);
            String category = claims.get("category", String.class);

            if (!"access".equals(category)) {
                throw new AuthException(AuthErrorCode.TOKEN_INVALID);
            }

            if (redisUtil.isBlackList(token)) {
                throw new AuthException(AuthErrorCode.TOKEN_BLACKLIST);
            }

            String memberId = claims.getSubject();

            if (memberId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                CustomUserDetails userDetails = new CustomUserDetails(
                        parseMemberId(memberId),
                        parseRole(claims)
                );
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);

                // ACH-EG-009(자정의 방문자) 판정. 요청 처리를 절대 막으면 안 되므로 실패해도
                // 로그만 남기고 넘어간다 - 히든 업적 하나 못 잡는 것보다 API 전체가 막히는
                // 사고가 훨씬 크다.
                try {
                    midnightAccessCommandService.recordIfMidnight(userDetails.getMemberId());
                } catch (RuntimeException e) {
                    log.warn("자정 접속 판정 중 오류가 발생했습니다. memberId={}", userDetails.getMemberId(), e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Long parseMemberId(String memberId) {
        try {
            return Long.valueOf(memberId);
        } catch (NumberFormatException e) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    private Role parseRole(Claims claims) {
        try {
            return Role.valueOf(claims.get("role", String.class));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }
    }
}
