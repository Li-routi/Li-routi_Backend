package com.lirouti.domain.wallet.converter;

import com.lirouti.domain.wallet.dto.response.WalletResDTO;
import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.enums.Currency;

import java.util.List;
import java.util.Map;

public final class WalletConverter {

    private WalletConverter() {
    }

    /**
     * 재화 종류 전부를 훑어 응답을 만든다. 지갑 행이 없는 재화는 0 이다.
     *
     * <p>지갑은 필요할 때 만들어지므로 <b>한 번도 재화를 받은 적 없는 회원에게는 행이 하나도
     * 없다.</b> 그것이 정상 상태라 조회 쪽에서 행을 만들지 않는다 — 조회가 쓰기를 하면
     * readOnly 트랜잭션이 깨지고, 목록을 열어 본 것만으로 빈 지갑이 쌓인다.
     */
    public static WalletResDTO.Balances toBalances(Map<Currency, MemberWallet> wallets) {
        List<WalletResDTO.Balance> balances = java.util.Arrays.stream(Currency.values())
                .map(currency -> WalletResDTO.Balance.builder()
                        .currency(currency)
                        .balance(wallets.containsKey(currency) ? wallets.get(currency).totalBalance() : 0)
                        .build())
                .toList();
        return WalletResDTO.Balances.builder().balances(balances).build();
    }
}
