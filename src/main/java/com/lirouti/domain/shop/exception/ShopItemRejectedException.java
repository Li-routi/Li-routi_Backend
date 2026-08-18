package com.lirouti.domain.shop.exception;

import com.lirouti.domain.shop.exception.code.error.ShopErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.Getter;

import java.util.List;

/**
 * 일부 아이템 때문에 구매 전체를 거절할 때.
 *
 * <p><b>어떤 아이템이 막혔는지를 함께 들고 간다.</b> 일괄 구매는 전부 되거나 전부 안 되거나인데,
 * 사유만 알려주고 대상을 안 알려주면 사용자는 <b>여섯 개 중 무엇을 빼야 할지 알 수 없다.</b>
 * 화면이 그 아이템만 선택 해제하고 바로 다시 시도할 수 있어야 한다.
 *
 * <p>사유는 {@link ShopErrorCode} 가 가른다 — 없는 아이템·판매 종료·이미 보유·중복 담기가 모두
 * "이 id 들 때문에 막혔다" 는 같은 모양이라, 예외를 사유마다 만들지 않고 코드로 구분한다.
 */
@Getter
public class ShopItemRejectedException extends GeneralException {

    private final List<Long> itemIds;

    public ShopItemRejectedException(ShopErrorCode errorCode, List<Long> itemIds) {
        super(errorCode);
        this.itemIds = List.copyOf(itemIds);
    }
}
