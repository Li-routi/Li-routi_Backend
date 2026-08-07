package com.lirouti.global.config;

import com.lirouti.global.auth.AccessDeniedHandlerImpl;
import com.lirouti.global.auth.AuthenticationEntryPointImpl;
import com.lirouti.global.auth.filter.JwtAuthFilter;
import com.lirouti.global.auth.filter.JwtExceptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
            // 로컬 개발용 토큰 발급. 토큰을 받으려는데 토큰이 필요하면 안 되므로 공개다.
            //
            // 이 목록은 프로파일과 무관하게 항상 적용되지만, 컨트롤러가 local 에서만 등록되고
            // 배포 산출물에는 아예 들어가지 않아 다른 환경에서는 404 다(DevTokenController).
            // 경로를 여는 것과 처리할 것이 있는 것은 다른 문제다.
            "/api/dev/token/**"
    };

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
