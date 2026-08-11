package com.lirouti.domain.charge.service.command;

import com.lirouti.domain.charge.converter.ChargeConverter;
import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.entity.ChargePayment;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.entity.ExchangeProduct;
import com.lirouti.domain.charge.exception.ChargeException;
import com.lirouti.domain.charge.exception.code.error.ChargeErrorCode;
import com.lirouti.domain.charge.repository.ChargePaymentRepository;
import com.lirouti.domain.charge.repository.ChargeProductRepository;
import com.lirouti.domain.charge.repository.ExchangeProductRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.service.WalletResult;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재화 충전(결제 시작)과 교환.
 *
 * <p><b>검증·지급은 아직 없다.</b> 포트원 조회 API 를 불러야 하는데 자격증명이 아직 없다.
 * 이 클래스는 그 앞 단계까지 — 결제를 시작해 대조 기준을 남기고, 재화끼리 바꾸는 것 — 을 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeCommandService {

    private final MemberRepository memberRepository;
    private final ChargeProductRepository chargeProductRepository;
    private final ExchangeProductRepository exchangeProductRepository;
    private final ChargePaymentRepository chargePaymentRepository;
    private final WalletService walletService;

    /**
     * 결제를 시작한다. <b>돈은 아직 오가지 않는다.</b>
     *
     * <p>여기서 남기는 것이 나중에 <b>대조할 기준</b>이다. 이 단계가 없으면 검증 때 클라이언트가
     * 보낸 금액을 믿는 수밖에 없고, 그러면 <b>100원 결제하고 11만원어치를 받는</b> 길이 열린다.
     *
     * <p>지급 내용도 함께 굳힌다 — 결제 도중 운영이 상품을 고쳐도 이 값으로 지급한다.
     */
    @Transactional
    public ChargeResDTO.Started startCharge(Long memberId, Long productId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        ChargeProduct product = chargeProductRepository.findById(productId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PRODUCT_NOT_FOUND));
        // 목록에서 감추는 것과 파는 것을 막는 것은 다르다 — 상품 id 를 아는 클라이언트는
        // 목록을 거치지 않고 여기로 바로 온다.
        if (!product.isActive()) {
            throw new ChargeException(ChargeErrorCode.PRODUCT_NOT_ON_SALE);
        }

        ChargePayment payment = chargePaymentRepository.save(ChargePayment.builder()
                .member(member)
                .product(product)
                .paymentId(newPaymentId())
                .requestedAt(LocalDateTime.now(TimeUtil.KST))
                .build());

        return ChargeConverter.toStarted(payment, orderNameOf(product));
    }

    /**
     * 재화를 바꾼다. <b>결제와 무관하다</b> — 지갑의 차감·지급만 쓴다.
     *
     * <p>차감을 먼저 한다. 지급이 먼저면 <b>모자란 사람에게 주고 나서 못 받는</b> 순간이 생긴다.
     * 잔액이 모자라면 지갑이 예외를 던지고 둘 다 되돌아간다.
     */
    @Transactional
    public ChargeResDTO.ExchangeResult exchange(Long memberId, Long productId,
                                                String idempotencyKey) {
        ExchangeProduct product = exchangeProductRepository.findById(productId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isActive()) {
            throw new ChargeException(ChargeErrorCode.PRODUCT_NOT_ON_SALE);
        }

        // 멱등 키는 클라이언트가 준다. 서버가 매번 새로 만들면 응답이 유실돼 재시도했을 때
        // 두 번 차감되고, 상품 id 로 만들면 같은 묶음을 두 번째 살 때 건너뛰어진다.
        // "이 사용자 동작 하나" 를 가리키는 값은 클라이언트만 안다.
        String key = "exchange:" + idempotencyKey;

        WalletResult from = walletService.deduct(new WalletCommand(
                memberId, product.getFromCurrency(), WalletTransactionType.EXCHANGE_OUT,
                key, "EXCHANGE_PRODUCT", product.getId()), product.getFromAmount());

        // 받는 쪽은 무상이다. 교환으로 얻은 재화에 유상 잔액을 만들지 않는다.
        WalletResult to = walletService.grant(new WalletCommand(
                memberId, product.getToCurrency(), WalletTransactionType.EXCHANGE_IN,
                key, "EXCHANGE_PRODUCT", product.getId()), 0, product.getToAmount());

        return ChargeConverter.toExchangeResult(product,
                from.paidBalance() + from.freeBalance(),
                to.paidBalance() + to.freeBalance());
    }

    /**
     * 결제 식별자. <b>추측할 수 없어야 한다</b> — 남의 것을 알아내면 완료를 대신 부를 수 있다.
     *
     * <p>회원 확인이 그것을 막지만, 식별자 자체가 순번이면 남의 결제가 몇 건인지가 새어 나간다.
     */
    private String newPaymentId() {
        return "charge_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String orderNameOf(ChargeProduct product) {
        int total = product.getRewardAmount() + product.getBonusAmount();
        return product.getRewardCurrency().name() + " " + total;
    }
}
