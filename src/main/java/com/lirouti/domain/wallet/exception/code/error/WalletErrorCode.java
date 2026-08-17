package com.lirouti.domain.wallet.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WalletErrorCode implements BaseErrorCode {

    /**
     * 잔액 부족. 402(Payment Required)가 아니라 409 인 이유는, 이 응답이 "결제하라"는 뜻이
     * 아니라 "지금 상태로는 이 요청을 처리할 수 없다"는 뜻이기 때문이다. 클라이언트는 이
     * 코드를 받아 충전 화면으로 유도한다.
     */
    INSUFFICIENT_BALANCE(
            HttpStatus.CONFLICT,
            "재화가 부족합니다.",
            "WALLET409_1"
    ),
    /**
     * 같은 멱등 키가 다른 종류의 거래로 다시 들어왔다. 호출부가 키를 잘못 만든 것이다.
     *
     * <p>조용히 되돌려주지 않는 이유는, 그러면 <b>차감이 안 됐는데 성공으로 보이기</b>
     * 때문이다. 시끄럽게 실패하는 편이 돈이 새는 것보다 낫다.
     */
    IDEMPOTENCY_KEY_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 요청 키가 다른 거래로 이미 사용되었습니다.",
            "WALLET409_2"
    ),
    INVALID_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "재화 수량은 1 이상이어야 합니다.",
            "WALLET400_1"
    ),
    /**
     * 무료 재화에 유상 잔액을 만들려 했다. <b>호출부의 잘못이지 사용자의 잘못이 아니다.</b>
     *
     * <p>막는 이유는 리워드 회수가 무료 재화에서 일어나기 때문이다. 그 재화에 유상 잔액이
     * 생기면 <b>회수가 환불 대상 재화를 깎는다</b> — 무상분을 다 쓴 사용자가 글을 지우면
     * 현금으로 산 몫에서 빠진다.
     */
    PAID_BALANCE_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "무료 재화에는 유상 잔액을 만들 수 없습니다.",
            "WALLET400_2"
    ),
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "회원 조회에 실패하였습니다.",
            "WALLET404_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
