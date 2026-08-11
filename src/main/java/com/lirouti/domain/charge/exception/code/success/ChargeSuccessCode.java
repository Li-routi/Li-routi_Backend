package com.lirouti.domain.charge.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChargeSuccessCode implements BaseSuccessCode {

    CHARGE_PRODUCT_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "충전 상품 목록 조회에 성공했습니다.",
            "CHARGE200_1"
    ),
    EXCHANGE_PRODUCT_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "교환 상품 목록 조회에 성공했습니다.",
            "CHARGE200_2"
    ),
    CHARGE_START_SUCCESS(
            HttpStatus.OK,
            "결제 준비에 성공했습니다.",
            "CHARGE200_3"
    ),
    EXCHANGE_SUCCESS(
            HttpStatus.OK,
            "재화 교환에 성공했습니다.",
            "CHARGE200_4"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
