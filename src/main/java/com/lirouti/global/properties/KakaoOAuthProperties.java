package com.lirouti.global.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "oauth.kakao")
public class KakaoOAuthProperties {
    @NotNull(message = "Kakao App ID는 필수입니다.")
    private Long appId;
}
