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
    INVALID_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "재화 수량은 1 이상이어야 합니다.",
            "WALLET400_1"
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
