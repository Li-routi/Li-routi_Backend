package com.lirouti.domain.charge.dto.response;

import com.lirouti.domain.wallet.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

public final class ChargeResDTO {

    private ChargeResDTO() {
    }

    @Schema(name = "ChargeProductItem", description = "현금으로 사는 재화 묶음")
    @Builder
    public record ChargeItem(
            @Schema(description = "상품 id") Long id,
            @Schema(description = "받는 재화") Currency rewardCurrency,
            @Schema(description = "유상으로 들어갈 수량") int rewardAmount,
            @Schema(description = "무상으로 들어갈 보너스") int bonusAmount,
            @Schema(description = "결제 금액(원)") int priceKrw,
            @Schema(description = "인기 뱃지") boolean popular
    ) {
    }

    @Schema(name = "ChargeProducts", description = "충전 상품 목록")
    @Builder
    public record ChargeItems(List<ChargeItem> items) {
    }

    @Schema(name = "ExchangeProductItem", description = "재화로 사는 재화 묶음")
    @Builder
    public record ExchangeItem(
            @Schema(description = "상품 id") Long id,
            @Schema(description = "내는 재화") Currency fromCurrency,
            @Schema(description = "내는 수량") int fromAmount,
            @Schema(description = "받는 재화") Currency toCurrency,
            @Schema(description = "받는 수량") int toAmount
    ) {
    }

    @Schema(name = "ExchangeProducts", description = "교환 상품 목록")
    @Builder
    public record ExchangeItems(List<ExchangeItem> items) {
    }

    /**
     * 결제 시작 결과. <b>클라이언트는 이 값으로 포트원 결제창을 연다.</b>
     *
     * <p>금액을 함께 내리지만 <b>이것은 화면 표시용</b>이다 — 검증은 서버가 저장한 값으로
     * 하므로, 클라이언트가 이 값을 고쳐 보내도 통과하지 않는다.
     */
    @Schema(name = "ChargeStarted", description = "결제 준비 결과")
    @Builder
    public record Started(
            @Schema(description = "포트원 상점 아이디") String storeId,
            @Schema(description = "채널 키. 어느 PG 로 결제할지를 가른다") String channelKey,
            @Schema(description = "서버가 만든 결제 식별자. 포트원 V2 의 paymentId") String paymentId,
            @Schema(description = "결제 금액(원)") int amount,
            @Schema(description = "화폐. 포트원 형식이다") String currency,
            @Schema(description = "주문명. 카드 명세서에 찍힌다") String orderName
    ) {
    }

    /**
     * 결제 검증·지급 결과.
     *
     * <p><b>{@link Started} 와 나눠 둔다.</b> 그쪽은 결제창을 여는 데 필요한 값 묶음이라
     * 상점 아이디·채널 키가 들어 있는데, 지급이 끝난 시점에는 쓸 데가 없다. 반대로 여기서
     * 필요한 지급 수량과 잔액이 그쪽에는 없다.
     *
     * <p>잔액을 함께 내리는 것은 <b>클라이언트가 잔액 조회를 한 번 더 부르지 않게</b> 하려는
     * 것이다. 지급 직후의 값을 서버가 이미 알고 있다.
     */
    @Builder
    @Schema(name = "ChargeSettled", description = "결제 검증·지급 결과")
    public record Settled(
            @Schema(description = "서버가 만든 결제 식별자") String paymentId,
            @Schema(description = "지급된 재화") Currency currency,
            @Schema(description = "유상으로 들어간 수량") int rewardAmount,
            @Schema(description = "무상으로 들어간 보너스") int bonusAmount,
            @Schema(description = "지급 후 유상 잔액") int paidBalance,
            @Schema(description = "지급 후 무상 잔액") int freeBalance
    ) {
    }

    @Schema(name = "ExchangeResult", description = "교환 결과")
    @Builder
    public record ExchangeResult(
            @Schema(description = "낸 재화") Currency fromCurrency,
            @Schema(description = "낸 수량") int fromAmount,
            @Schema(description = "받은 재화") Currency toCurrency,
            @Schema(description = "받은 수량") int toAmount,
            @Schema(description = "교환 후 낸 재화의 잔액") int fromBalance,
            @Schema(description = "교환 후 받은 재화의 잔액") int toBalance
    ) {
    }
}
