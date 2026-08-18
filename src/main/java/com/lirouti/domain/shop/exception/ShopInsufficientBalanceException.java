package com.lirouti.domain.shop.exception;

import com.lirouti.domain.shop.exception.code.error.ShopErrorCode;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.Getter;

import java.util.List;

/**
 * 재화가 모자라 구매를 거절할 때.
 *
 * <p><b>얼마가 모자란지를 함께 들고 간다.</b> "부족합니다" 만으로는 사용자가 무엇을 해야 하는지
 * 알 수 없다 — 충전하러 갈지, 아이템을 빼고 다시 고를지를 정하려면 수량이 필요하다.
 *
 * <p><b>모자란 재화를 전부 담는다.</b> 재화가 섞인 구매는 파란 보석과 주황 보석이 동시에 모자랄
 * 수 있다. 먼저 걸린 하나만 알려주면 사용자가 그것을 채우고 돌아와 <b>또 막힌다</b> — 그래서
 * 서비스가 차감을 시작하기 전에 모든 재화를 검사해 여기 모아 넘긴다.
 *
 * <p>{@link GeneralException} 은 코드만 싣기 때문에 이 값이 들어갈 자리가 없다. 그래서 예외를
 * 따로 두고, 전역 처리기가 이 타입일 때만 실패 응답에 본문을 함께 싣는다. 리워드 회수가 모자랄
 * 때 쓰는 방식과 같다.
 */
@Getter
public class ShopInsufficientBalanceException extends GeneralException {

    private final List<Shortage> shortages;

    public ShopInsufficientBalanceException(List<Shortage> shortages) {
        super(ShopErrorCode.INSUFFICIENT_BALANCE);
        this.shortages = List.copyOf(shortages);
    }

    /**
     * 재화 한 종류가 얼마나 모자란지.
     *
     * @param required 이 재화로 내야 하는 총액
     * @param balance  지금 가진 수량
     */
    public record Shortage(Currency currency, int required, int balance) {

        /** 몇 개를 더 채우면 살 수 있는지. */
        public int shortfall() {
            return Math.max(0, required - balance);
        }
    }
}
