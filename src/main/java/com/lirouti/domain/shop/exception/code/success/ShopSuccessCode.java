package com.lirouti.domain.shop.exception.code.success;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ShopSuccessCode implements BaseSuccessCode {

    SHOP_ITEM_LIST_FETCH_SUCCESS(
            HttpStatus.OK,
            "상점 아이템 목록 조회에 성공했습니다.",
            "SHOP200_1"
    ),
    SHOP_ITEM_PURCHASE_SUCCESS(
            HttpStatus.OK,
            "아이템 구매에 성공했습니다.",
            "SHOP200_2"
    ),
    AVATAR_FETCH_SUCCESS(
            HttpStatus.OK,
            "아바타 착용 상태 조회에 성공했습니다.",
            "SHOP200_3"
    ),
    AVATAR_EQUIP_SUCCESS(
            HttpStatus.OK,
            "아바타 착용 저장에 성공했습니다.",
            "SHOP200_4"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
