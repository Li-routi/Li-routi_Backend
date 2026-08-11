package com.lirouti.domain.charge.converter;

import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.entity.ChargePayment;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.entity.ExchangeProduct;

import java.util.List;

public final class ChargeConverter {

    private ChargeConverter() {
    }

    public static ChargeResDTO.ChargeItems toChargeItems(List<ChargeProduct> products) {
        return ChargeResDTO.ChargeItems.builder()
                .items(products.stream()
                        .map(p -> ChargeResDTO.ChargeItem.builder()
                                .id(p.getId())
                                .rewardCurrency(p.getRewardCurrency())
                                .rewardAmount(p.getRewardAmount())
                                .bonusAmount(p.getBonusAmount())
                                .priceKrw(p.getPriceKrw())
                                .popular(p.isPopular())
                                .build())
                        .toList())
                .build();
    }

    public static ChargeResDTO.ExchangeItems toExchangeItems(List<ExchangeProduct> products) {
        return ChargeResDTO.ExchangeItems.builder()
                .items(products.stream()
                        .map(p -> ChargeResDTO.ExchangeItem.builder()
                                .id(p.getId())
                                .fromCurrency(p.getFromCurrency())
                                .fromAmount(p.getFromAmount())
                                .toCurrency(p.getToCurrency())
                                .toAmount(p.getToAmount())
                                .build())
                        .toList())
                .build();
    }

    public static ChargeResDTO.Started toStarted(ChargePayment payment, String orderName) {
        return ChargeResDTO.Started.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getExpectedAmount())
                .orderName(orderName)
                .build();
    }

    public static ChargeResDTO.ExchangeResult toExchangeResult(
            ExchangeProduct product, int fromBalance, int toBalance) {
        return ChargeResDTO.ExchangeResult.builder()
                .fromCurrency(product.getFromCurrency())
                .fromAmount(product.getFromAmount())
                .toCurrency(product.getToCurrency())
                .toAmount(product.getToAmount())
                .fromBalance(fromBalance)
                .toBalance(toBalance)
                .build();
    }
}
