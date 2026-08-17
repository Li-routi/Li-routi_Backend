package com.lirouti.domain.wallet.enums;

/**
 * 거래 종류. <b>왜 잔액이 움직였는지</b>를 남긴다.
 *
 * <p>결제 분쟁이나 잔액 문의는 "그때 얼마가 들어왔고 어디에 썼는지"를 답해야 하는데,
 * 잔액 하나로는 답할 수 없다. 지급과 회수를 따로 둔 것도 같은 이유다 — 같은 종류에 부호만
 * 다르게 쌓으면 "몇 번 지급됐나"를 세는 것과 "순증이 얼마인가"가 구분되지 않는다.
 */
public enum WalletTransactionType {

    /** 챌린지 인증 통과 리워드 지급. */
    CHALLENGE_REWARD,

    /** 인증을 지워 리워드를 회수. */
    CHALLENGE_REWARD_CLAWBACK,

    /** 아바타 아이템 구매 차감. */
    PURCHASE,

    /** 현금 결제로 충전. */
    TOPUP,

    /** 재화 간 교환에서 <b>나간</b> 쪽. */
    EXCHANGE_OUT,

    /** 재화 간 교환에서 <b>들어온</b> 쪽. 교환은 재화가 달라 한 행에 담을 수 없다. */
    EXCHANGE_IN,

    /** 결제 취소로 회수. */
    REFUND,

    /** 운영 보정. */
    ADMIN_ADJUST,

    /** 업적 보상 지급. */
    ACHIEVEMENT_REWARD
}
