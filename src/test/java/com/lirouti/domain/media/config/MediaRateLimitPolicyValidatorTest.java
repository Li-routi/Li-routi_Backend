package com.lirouti.domain.media.config;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.global.properties.RateLimitProperties;
import com.lirouti.global.ratelimit.RateLimitGuard;
import com.lirouti.global.ratelimit.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("MediaRateLimitPolicyValidator 테스트")
class MediaRateLimitPolicyValidatorTest {

    private MediaRateLimitPolicyValidator validatorWith(String... definedPolicies) {
        RateLimitProperties properties = new RateLimitProperties();
        for (String name : definedPolicies) {
            RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
            policy.setLimit(10);
            policy.setWindow(Duration.ofMinutes(10));
            properties.getPolicies().put(name, policy);
        }
        RateLimitGuard guard =
                new RateLimitGuard(mock(RateLimiter.class), properties, Clock.systemUTC());
        return new MediaRateLimitPolicyValidator(guard);
    }

    /** enum 이 실제로 가리키는 이름들. 여기 하드코딩하면 검증이 자기 자신을 보게 되어 의미가 없다. */
    private String[] policiesDeclaredByEnum() {
        return Arrays.stream(MediaPurpose.values())
                .map(MediaPurpose::getRateLimitPolicy)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toArray(String[]::new);
    }

    @Test
    @DisplayName("enum이 가리키는 정책이 모두 설정에 있으면 통과한다")
    void verifyPoliciesExist_AllDefined_Passes() {
        MediaRateLimitPolicyValidator validator = validatorWith(policiesDeclaredByEnum());

        assertThatCode(validator::verifyPoliciesExist).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정책이 하나라도 빠지면 부팅을 실패시킨다 — 제한이 조용히 사라지는 것을 막는다")
    void verifyPoliciesExist_MissingPolicy_FailsFast() {
        // given: 인증 정책만 정의하고 프로필 정책을 빠뜨린다
        MediaRateLimitPolicyValidator validator = validatorWith("media-presign-verification");

        // when & then
        assertThatThrownBy(validator::verifyPoliciesExist)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("media-presign-profile")
                .hasMessageContaining("PROFILE");
    }

    @Test
    @DisplayName("정책이 하나도 없으면 어떤 용도가 비었는지 전부 알려준다")
    void verifyPoliciesExist_NothingDefined_ReportsEveryPurpose() {
        MediaRateLimitPolicyValidator validator = validatorWith();

        assertThatThrownBy(validator::verifyPoliciesExist)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHALLENGE_VERIFICATION")
                .hasMessageContaining("GROUP_ROUTINE_VERIFICATION")
                .hasMessageContaining("MEMBER_ROUTINE_VERIFICATION")
                .hasMessageContaining("PROFILE")
                // 발급 경로가 없어 정책이 null 인 용도는 검사 대상이 아니다
                .hasMessageNotContaining("CHAT_EMOTICON");
    }
}
