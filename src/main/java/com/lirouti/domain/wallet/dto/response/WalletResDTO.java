package com.lirouti.domain.wallet.dto.response;

import com.lirouti.domain.wallet.enums.Currency;
import lombok.Builder;

import java.util.List;

public final class WalletResDTO {

    private WalletResDTO() {
    }

    /**
     * 상점 헤더에 뜨는 잔액. 재화 종류마다 한 건씩 <b>항상 전부</b> 내려간다.
     *
     * <p>지갑 행이 없는 재화도 0 으로 실린다. 없는 것을 빼고 내리면 클라이언트가 "안 온 재화"를
     * 따로 처리해야 하고, 화면은 어차피 둘 다 그린다.
     */
    @Builder
    public record Balances(
            List<Balance> balances
    ) {
    }

    /**
     * 재화 하나의 잔액.
     *
     * <p>유상·무상 구분은 내리지 않는다. 화면이 총합만 쓰고, 나누어 보여 줄 자리(환불 안내
     * 같은 것)가 생기면 그때 필드를 더하면 된다 — 응답에 필드를 더하는 것은 하위호환이다.
     */
    @Builder
    public record Balance(
            Currency currency,
            int balance
    ) {
    }
}
