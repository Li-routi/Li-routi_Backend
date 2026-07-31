package com.lirouti.global.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * AI 심사 설정의 검증.
 *
 * <p>지키려는 것은 <b>"환경변수가 안 들어왔을 때 부팅이 실패하는가"</b>다.
 *
 * <p>{@code @NotBlank} 만으로는 잡히지 않는다. 기본값 없이 {@code ${ANTHROPIC_API_KEY}} 만
 * 선언해도, 해석되지 못한 플레이스홀더가 그 문자열 그대로 바인딩되어 빈 값이 아니게 된다.
 * 실제로 그렇게 배포된 적이 있다 — 앱은 정상 부팅했고 심사는 fail-open 이라 조용히 꺼진
 * 상태로 돌았다.
 */
@DisplayName("AI 심사 설정 검증 테스트")
class AiReviewPropertiesTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private AiReviewProperties properties(String apiKey) {
        AiReviewProperties properties = new AiReviewProperties();
        properties.setApiKey(apiKey);
        properties.setModel("claude-haiku-4-5-20251001");
        properties.setTimeout(Duration.ofSeconds(20));
        return properties;
    }

    private Set<ConstraintViolation<AiReviewProperties>> validate(String apiKey) {
        return validator.validate(properties(apiKey));
    }

    @Test
    @DisplayName("미해결 플레이스홀더가 그대로 바인딩되면 잡는다 — @NotBlank만으로는 통과한다")
    void validate_UnresolvedPlaceholder_IsRejected() {
        assertThat(validate("${ANTHROPIC_API_KEY}")).isNotEmpty();
    }

    @Test
    @DisplayName("빈 값도 잡는다")
    void validate_Blank_IsRejected() {
        assertThat(validate("")).isNotEmpty();
        assertThat(validate("   ")).isNotEmpty();
    }

    @Test
    @DisplayName("형식이 다른 값은 잡는다")
    void validate_WrongFormat_IsRejected() {
        assertThat(validate("not-a-key")).isNotEmpty();
    }

    @Test
    @DisplayName("정상 키는 통과한다")
    void validate_ValidKey_Passes() {
        assertThat(validate("sk-ant-api03-abcDEF123_-xyz")).isEmpty();
    }
}
