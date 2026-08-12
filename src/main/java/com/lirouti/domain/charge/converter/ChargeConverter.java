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

    /**
     * 결제창을 열 때 필요한 값을 <b>전부</b> 내린다.
     *
     * <p>프론트가 상점 아이디·채널 키를 따로 들고 있으면 채널을 바꿀 때 프론트도 다시
     * 배포해야 한다. 서버가 내리면 설정만 고치면 된다.
     */
    public static ChargeResDTO.Started toStarted(ChargePayment payment, String orderName,
                                                 String storeId, String channelKey) {
        return ChargeResDTO.Started.builder()
                .storeId(storeId)
                .channelKey(channelKey)
                .paymentId(payment.getPaymentId())
                .amount(payment.getExpectedAmount())
                .currency("CURRENCY_KRW")
                .orderName(orderName)
                .build();
    }

    /**
     * 지급이 끝난 결제를 응답으로 바꾼다.
     *
     * <p>지급 수량은 <b>결제 시작 때 굳혀 둔 값</b>이다. 상품이 그 사이 바뀌어도 실제로 준
     * 것과 응답이 어긋나지 않는다.
     */
    public static ChargeResDTO.Settled toSettled(ChargePayment payment,
                                                 int paidBalance, int freeBalance) {
        return ChargeResDTO.Settled.builder()
                .paymentId(payment.getPaymentId())
                .currency(payment.getRewardCurrency())
                .rewardAmount(payment.getRewardAmount())
                .bonusAmount(payment.getBonusAmount())
                .paidBalance(paidBalance)
                .freeBalance(freeBalance)
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
