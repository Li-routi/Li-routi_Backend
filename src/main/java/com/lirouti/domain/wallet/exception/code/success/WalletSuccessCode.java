package com.lirouti.domain.wallet.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WalletSuccessCode implements BaseSuccessCode {

    WALLET_BALANCE_FETCH_SUCCESS(
            HttpStatus.OK,
            "재화 잔액 조회에 성공했습니다.",
            "WALLET200_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
