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
     * <p>둘. <b>오탐이 번지는 것을 멈출 수 있다.</b> 값을 올리면 그 뒤로는 덜 가려진다.
     *
     * <p><b>다만 이미 가려진 것은 되살아나지 않는다.</b> 조회는 hidden_at 이 NULL 인지만 보는데
     * 그 값을 해제하는 경로가 없다. 임계값을 올려도 새로 가려지는 것만 줄어들 뿐, 기존 오탐은
     * 그대로 가려져 있다. 되살리려면 그 행의 hidden_at 을 DB 에서 직접 NULL 로 되돌려야 한다.
     * 관리자 API 는 이 임시 조치의 범위를 넘는다고 보아 넣지 않았다.
     */
    @Positive(message = "신고 숨김 임계값은 양수여야 합니다.")
    private int hideThreshold = 3;
}
