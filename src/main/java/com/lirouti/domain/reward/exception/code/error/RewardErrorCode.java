package com.lirouti.domain.reward.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RewardErrorCode implements BaseErrorCode {

    /**
     * 받은 리워드를 되돌릴 재화가 모자라 삭제를 거절한다.
     *
     * <p>부채를 남기지 않기 위해서다. 미상환을 허용하면 그만큼 제재를 걸어야 하는데, 제재
     * 중에는 인증을 못 해 갚을 수단이 없다 — 빠져나올 수 없는 상태가 된다.
     */
    REWARD_CLAWBACK_INSUFFICIENT(
            HttpStatus.CONFLICT,
            "받은 재화가 부족해 삭제할 수 없습니다.",
            "REWARD409_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
