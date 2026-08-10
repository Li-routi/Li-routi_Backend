package com.lirouti.domain.reward.enums;

/**
 * 재화를 지급한 사유.
 *
 * <p>지금은 {@link #VERIFICATION} 하나만 쓴다. 그럼에도 컬럼으로 두는 것은 <b>나중에 사유가
 * 늘 때 테이블을 다시 만들지 않기 위해서</b>다. 유니크 키가 이 값을 포함하므로, 사유가 다르면
 * 같은 대상에 대해 따로 지급할 수 있다.
 */
public enum RewardReason {

    /** 챌린지 인증 한 건이 심사를 통과했다. {@code reference_id} 는 인증 id 다. */
    VERIFICATION,

    /** 스트릭 마일스톤(7일·30일 연속 등). 아직 쓰지 않는다. */
    STREAK_MILESTONE
}
