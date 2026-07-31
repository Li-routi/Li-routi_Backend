package com.lirouti.global.config;

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

    private static final String[] PUBLIC_URIS = {
            "/api/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/health"
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
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtExceptionFilter, JwtAuthFilter.class);

        return http.build();
    }
}
