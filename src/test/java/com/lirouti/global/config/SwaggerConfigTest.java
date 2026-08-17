package com.lirouti.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Swagger 그룹 설정 테스트")
class SwaggerConfigTest {

    private final SwaggerConfig swaggerConfig = new SwaggerConfig();

    @Test
    @DisplayName("관리자 API는 별도 그룹으로 묶고 일반 인증 그룹에서는 제외한다")
    void groupedOpenApi_Admin_IsSeparatedFromPrivateApi() {
        GroupedOpenApi adminApi = swaggerConfig.adminAPI();
        GroupedOpenApi privateApi = swaggerConfig.privateAPI();

        assertThat(adminApi.getGroup()).isEqualTo("3. 관리자 전용");
        assertThat(adminApi.getPathsToMatch()).containsExactly("/api/admin/**");
        assertThat(privateApi.getPathsToExclude())
                .containsExactly("/api/auth/**", "/api/admin/**");
    }
}
