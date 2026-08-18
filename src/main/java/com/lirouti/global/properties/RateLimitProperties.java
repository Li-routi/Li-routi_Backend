package com.lirouti.global.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 요청 빈도 제한 정책(#23).
 *
 * 한도를 코드가 아니라 설정에 두는 이유는, 운영에서 값을 조정할 때 코드를 고치지 않기
 * 위해서다. 코드에는 <b>정책 이름만</b> 두고 숫자는 여기서 관리한다.
 *
 * <p>이름을 참조하는 곳이 둘이다 — 핸들러에 붙는 {@code @RateLimit} 값, 그리고 요청 값에 따라
 * 정책이 갈리는 경우의 도메인 enum({@code MediaPurpose}). 없는 이름을 가리키면 제한이 걸리지
 * 않고 ERROR 로그만 남으므로, 후자는 {@code MediaRateLimitPolicyValidator} 가 부팅 때 확인한다.
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
