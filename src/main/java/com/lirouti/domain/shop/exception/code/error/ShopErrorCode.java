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
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
