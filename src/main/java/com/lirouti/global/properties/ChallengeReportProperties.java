package com.lirouti.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 신고 누적 자동 숨김 설정.
 *
 * 데모 전까지의 임시 조치다. 장기적으로는 AI 심사와 관리자 UI 가 판단·복구를 다룬다.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "challenge.report")
public class ChallengeReportProperties {

    /**
     * 이 수만큼 신고가 쌓이면 인증을 전체 회원에게 가린다.
     *
     * <p>코드가 아니라 설정에 두는 이유가 둘이다.
     *
     * <p>하나. <b>적정값이 사용자 수에 따라 달라진다.</b> 실사용자가 거의 없는 지금 5는 전체의
     * 큰 비율이고, 사용자가 늘면 반대로 너무 낮아진다.
     *
     * <p>둘. <b>이게 유일한 복구 수단이다.</b> 한 번 가려진 것을 되살릴 관리자 API 가 없어서,
     * 데모 중 오탐이 나면 이 값을 올려 되돌린다. 관리자 API 는 임시 조치의 범위를 넘는다고 봤다.
     */
    @Positive(message = "신고 숨김 임계값은 양수여야 합니다.")
    private int hideThreshold = 3;
}
