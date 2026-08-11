package com.lirouti.domain.charge.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChargeErrorCode implements BaseErrorCode {

    PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "상품을 찾을 수 없습니다.",
            "CHARGE404_1"
    ),

    /**
     * 결제를 찾을 수 없다. <b>남의 결제를 부른 경우도 여기로 온다.</b>
     *
     * <p>"당신 것이 아니다" 와 "없다" 를 구분해 답하면 남의 결제가 존재하는지가 새어 나간다.
     * 인증 게시글 삭제에서 넷을 같은 404 로 묶은 것과 같은 기준이다.
     */
    PAYMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "결제 정보를 찾을 수 없습니다.",
            "CHARGE404_2"
    ),
    PRODUCT_NOT_ON_SALE(
            HttpStatus.CONFLICT,
            "판매가 종료된 상품입니다.",
            "CHARGE409_1"
    ),

    /** 포트원이 말하는 금액이 결제 시작 때 기록한 값과 다르다. <b>금액 조작을 여기서 막는다.</b> */
    AMOUNT_MISMATCH(
            HttpStatus.CONFLICT,
            "결제 금액이 일치하지 않습니다.",
            "CHARGE409_2"
    ),
    PAYMENT_NOT_COMPLETED(
            HttpStatus.CONFLICT,
            "아직 완료되지 않은 결제입니다.",
            "CHARGE409_3"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
