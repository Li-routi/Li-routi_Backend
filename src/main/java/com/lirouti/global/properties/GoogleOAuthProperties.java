package com.lirouti.global.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "oauth.google")
public class GoogleOAuthProperties {
    @NotBlank(message = "Google Web Client ID는 필수입니다.")
    private String webClientId;

    @NotEmpty(message = "Google 허용 issuer는 하나 이상 필요합니다.")
    private Set<@NotBlank(message = "Google 허용 issuer는 비어 있을 수 없습니다.") String> allowedIssuers = Set.of();
}
