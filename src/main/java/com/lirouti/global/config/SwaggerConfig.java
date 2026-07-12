package com.lirouti.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    private static final String SCHEME_NAME = "JWT_AUTH";

    @Bean
    public OpenAPI openAPI() {
        // SecurityScheme 정의
        SecurityScheme jwtScheme = new SecurityScheme()
                .name(SCHEME_NAME) // 스키마 이름 설정
                .type(SecurityScheme.Type.HTTP) // http 프로토콜 사용 명시
                .scheme("bearer") // Bearer 인증 방식 설정
                .bearerFormat("JWT") // 토큰 형식이 JWT임을 명시
                .description("발급받은 JWT 토큰을 입력하세요."); // 스웨거 UI에 보일 설명

        // 전역적으로 보안 요구사항 설정
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(SCHEME_NAME);

        return new OpenAPI()
                .info(apiInfo()) // API 문서의 기본 정보 설정
                .addSecurityItem(securityRequirement) // 전역 보안 요구사항 추가
                .components(new Components().addSecuritySchemes(SCHEME_NAME, jwtScheme)); // 컴포넌트에 보안 스키마 추가
    }

    private Info apiInfo() {
        return new Info()
                .title("Ri-routi API") // API 문서의 제목
                .description("Ri-routi 프로젝트 API 문서입니다.") // API 문서의 설명
                .version("1.0.0"); // API 문서의 버전
    }

    @Bean
    public GroupedOpenApi publicAPI() {
        // 인증이 필요하지 않은 API들을 그룹핑
        return GroupedOpenApi.builder()
                .group("1. 인증 불필요")
                .pathsToMatch("/api/auth/**") // 해당 경로로 시작하는 모든 API를 이 그룹에 포함
                .build();
    }

    @Bean
    public GroupedOpenApi privateAPI() {
        // 인증이 필요한 API들을 그룹핑
        return GroupedOpenApi.builder()
                .group("2. JWT 인증 필요")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/auth/**") // 해당 경로는 이 그룹에서 제외
                .build();
    }
}
