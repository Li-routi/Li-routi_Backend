package com.lirouti.domain.shop.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ShopErrorCode implements BaseErrorCode {

    ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "아이템을 찾을 수 없습니다.",
            "SHOP404_1"
    ),

    /**
     * 판매가 내려간 아이템을 사려 한 경우.
     *
     * <p>목록에서 감추는 것과 구매를 막는 것은 다르다 — 아이템 id 를 아는 클라이언트는 목록을
     * 거치지 않고 구매를 부를 수 있다.
     */
    ITEM_NOT_ON_SALE(
            HttpStatus.CONFLICT,
            "판매가 종료된 아이템입니다.",
            "SHOP409_1"
    ),

    /** 아바타 아이템은 영구 보유라 두 번 살 이유가 없다. 두 번 결제되면 그대로 손해다. */
    ALREADY_OWNED(
            HttpStatus.CONFLICT,
            "이미 보유한 아이템입니다.",
            "SHOP409_2"
    ),

    /** 미보유 착용은 구매를 건너뛰는 길이 된다. */
    ITEM_NOT_OWNED(
            HttpStatus.FORBIDDEN,
            "보유하지 않은 아이템은 착용할 수 없습니다.",
            "SHOP403_1"
    ),

    /** 같은 자리에 둘을 보내면 어느 쪽을 입힐지 알 수 없다. */
    DUPLICATE_SLOT(
            HttpStatus.BAD_REQUEST,
            "같은 자리에 두 개를 착용할 수 없습니다.",
            "SHOP400_1"
    ),

    /**
     * 구매 요청에 같은 아이템이 두 번 들어온 경우.
     *
     * <p><b>조용히 하나로 합치지 않는다.</b> 착용은 두 번 보내도 뜻이 분명해 합쳐도 되지만,
     * 구매는 화면이 이미 "6개 · 4,800" 처럼 개수와 금액을 보여준 뒤에 들어온다. 서버가 말없이
     * 하나를 지우면 <b>사용자가 본 금액과 실제 결제액이 어긋난다.</b>
     */
    DUPLICATE_ITEM(
            HttpStatus.BAD_REQUEST,
            "같은 아이템을 두 번 담을 수 없습니다.",
            "SHOP400_2"
    ),

    /**
     * 재화가 모자라 구매를 거절한 경우.
     *
     * <p>지갑의 {@code WALLET409_1} 을 그대로 쓰지 않는 이유는 <b>부족분을 함께 실어야</b>
     * 하기 때문이다. 재화가 섞인 구매는 모자란 재화가 둘일 수 있어, 화면이 "얼마가 더
     * 필요한지" 를 재화별로 그려야 한다.
     */
    INSUFFICIENT_BALANCE(
            HttpStatus.CONFLICT,
            "재화가 부족합니다.",
            "SHOP409_3"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
