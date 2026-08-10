package com.lirouti.domain.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public final class RewardResDTO {

    private RewardResDTO() {
    }

    /**
     * 회수할 재화가 모자라 삭제가 거절됐을 때 함께 내리는 값.
     *
     * <p>"삭제할 수 없습니다" 만으로는 사용자가 무엇을 해야 하는지 알 수 없다. <b>몇 개가
     * 모자란지</b>를 알아야 행동할 수 있다.
     */
    @Schema(name = "RewardClawbackShortfall", description = "회수 재화 부족 안내")
    @Builder
    public record ClawbackShortfall(
            @Schema(description = "되돌려야 하는 수량") int required,
            @Schema(description = "지금 가진 수량") int balance,
            @Schema(description = "더 필요한 수량") int shortfall
    ) {
    }
}
