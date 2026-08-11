package com.lirouti.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.AccessDeniedHandlerImpl;
import com.lirouti.global.auth.AuthenticationEntryPointImpl;
import com.lirouti.global.auth.filter.JwtAuthFilter;
import com.lirouti.global.auth.filter.JwtExceptionFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;

    private static final String[] PUBLIC_URIS = {
            "/api/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/health",
            // 포트원이 부르는 자리라 JWT 를 붙일 수 없다. 대신 본문을 믿지 않는다 —
            // 거기 실린 결제 식별자로 포트원에 다시 물어보고, 그 답으로만 지급한다.
            // 위조한 본문을 보내도 포트원이 모르는 결제면 아무 일도 일어나지 않는다.
            "/api/shop/charges/webhook"
    };
    private static final String ADMIN_CHAT_EMOTICON_URI = "/api/admin/chat/emoticons/**";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        // 공개 경로는 PUBLIC_URIS가 전부다. 그 밖의 모든 요청은 JWT가 필요하다(#77).
                        //
                        // 챌린지 목록·상세도 여기 포함된다. 예전에는 마스터 데이터라는 이유로 열어 뒀는데,
                        // 로그인 구현이 끝나기 전의 한시 조치였다. 이 서비스에는 게스트 개념이 없다 —
                        // 로그인하지 않으면 내부 기능에 닿지 못하는 것이 정상이다.
                        auth.requestMatchers(PUBLIC_URIS).permitAll()
                                .requestMatchers(ADMIN_CHAT_EMOTICON_URI).hasAuthority(Role.ROLE_ADMIN.name())
                                .anyRequest().authenticated()
                )
                // 인증·인가 실패를 ApiResponse 형태로 내보낸다. 등록하지 않으면 스프링 기본
                // 동작이 본문 0바이트 403을 내보내, 클라이언트가 파싱에 실패하고 코드도 메시지도
                // 받지 못한다. 미인증은 401, 권한 부족은 403으로 갈린다.
                //
                // 둘을 항상 함께 둔다 — 하나만 등록하면 나머지 경우가 기본 동작으로 샌다.
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtExceptionFilter, JwtAuthFilter.class);

        return http.build();
    }
}
