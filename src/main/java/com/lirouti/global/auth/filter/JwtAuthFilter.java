package com.lirouti.global.auth.filter;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

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
                /*
                * 짧은 만료 시간의 액세스 토큰은 회원 DB를 매 요청마다 조회하지 않고,
                * JWT 서명·클레임과 Redis 블랙리스트만 검증해 인증 정보를 구성한다.
                * DB 회원 상태 변경은 토큰 만료 후 반영되지만, 블랙리스트에 등록된 토큰은 다음 요청부터 차단된다.
                */            
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
