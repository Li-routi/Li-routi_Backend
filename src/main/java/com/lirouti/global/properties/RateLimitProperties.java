package com.lirouti.global.properties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 요청 빈도 제한 정책(#23).
 *
 * 한도를 애노테이션이 아니라 설정에 두는 이유는, 운영에서 값을 조정할 때 코드를 고치지 않기
 * 위해서다. 컨트롤러에는 {@code @RateLimit("media-presign")}처럼 <b>정책 이름만</b> 적고
 * 숫자는 여기서 관리한다.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    /**
     * 전체 차단 스위치. Redis 장애나 오탐으로 정상 사용자가 막힐 때 재배포 없이 끄기 위한 탈출구다
     * (AWS_S3_BYTE_VALIDATION_ENABLED와 같은 목적).
     */
    private boolean enabled = true;

    /** 정책 이름 → 한도. {@code @RateLimit}의 값이 이 맵의 키와 맞아야 한다. */
    @Valid
    private Map<String, Policy> policies = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Policy {
        /** 창(window) 하나당 허용 요청 수. */
        @Positive(message = "요청 한도는 양수여야 합니다.")
        private int limit;

        /** 창 길이. 예: PT1H */
        @NotNull(message = "제한 창 길이는 필수입니다.")
        private Duration window;
    }
}
