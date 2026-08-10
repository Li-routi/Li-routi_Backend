package com.lirouti.domain.reward.exception;

import com.lirouti.domain.reward.exception.code.error.RewardErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.Getter;

/**
 * 회수할 재화가 모자라 삭제를 거절할 때.
 *
 * <p><b>얼마가 모자란지를 함께 들고 간다.</b> "삭제할 수 없습니다" 만으로는 사용자가 무엇을
 * 해야 하는지 알 수 없다 — 몇 개를 더 모으면 되는지 알아야 행동할 수 있다.
 *
 * <p>{@link GeneralException} 은 코드만 싣기 때문에 이 값이 들어갈 자리가 없다. 그래서 예외를
 * 따로 두고, 전역 처리기가 이 타입일 때만 실패 응답에 본문을 함께 싣는다.
 */
@Getter
public class RewardClawbackException extends GeneralException {

    private final int required;
    private final int balance;

    public RewardClawbackException(int required, int balance) {
        super(RewardErrorCode.REWARD_CLAWBACK_INSUFFICIENT);
        this.required = required;
        this.balance = balance;
    }

    /** 몇 개가 더 있어야 지울 수 있는지. */
    public int shortfall() {
        return Math.max(0, required - balance);
    }
}
