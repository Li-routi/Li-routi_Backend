package com.lirouti.domain.charge.service.command;

import com.lirouti.domain.charge.client.PortOneClient;
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
    private final PortOneClient portOneClient;

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
     * 결제를 검증하고 재화를 지급한다.
     *
     * <p><b>요청 본문의 값으로 판단하지 않는다.</b> 결제 식별자로 <b>인증된 회원의</b> 행을
     * 찾고, 없으면 거절한다 — 회원을 안 보면 남의 식별자를 알아낸 사람이 자기 계정으로 이것을
     * 불러 <b>남의 결제로 자기 재화를 채울 수 있다.</b>
     *
     * <p>지급은 <b>결제 시작 때 굳혀 둔 스냅샷</b>으로 한다. 상품을 다시 읽으면 그 사이 운영이
     * 고친 값이 나온다.
     *
     * <p>이미 지급된 결제면 <b>조용히 통과한다.</b> 완료 요청과 웹훅이 둘 다 오는 것이 정상이다.
     */
    @Transactional
    public ChargeResDTO.Started complete(Long memberId, String paymentId) {
        // 회원으로 좁혀 확인한다. 여기서 걸러야 남의 결제를 쓰는 길이 막힌다.
        chargePaymentRepository.findByPaymentIdAndMemberId(paymentId, memberId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PAYMENT_NOT_FOUND));
        return settle(paymentId);
    }

    /**
     * 웹훅으로 들어온 결제를 처리한다.
     *
     * <p>완료 요청은 <b>클라이언트가 불러 준다.</b> 결제 직후 앱이 죽거나 네트워크가 끊기면
     * <b>돈은 나갔는데 재화가 없는</b> 상태가 남으므로 웹훅이 그것을 메운다.
     *
     * <p><b>웹훅 payload 는 믿지 않는다.</b> 거기 실린 식별자로 포트원에 다시 물어본다 —
     * 웹훅 주소는 공개되어 있어 아무나 위조한 본문을 보낼 수 있다.
     */
    @Transactional
    public void handleWebhook(String paymentId) {
        settle(paymentId);
    }

    /**
     * 검증과 지급. 완료 요청과 웹훅이 같은 길을 쓴다.
     *
     * <p><b>행을 잠그고 상태를 본다.</b> 둘이 동시에 들어오면 하나만 지급해야 하는데, 읽고 나서
     * 쓰면 둘 다 통과한다.
     */
    private ChargeResDTO.Started settle(String paymentId) {
        ChargePayment payment = chargePaymentRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PAYMENT_NOT_FOUND));

        if (payment.isPaid()) {
            // 완료 요청과 웹훅이 둘 다 오는 것이 정상이다. 나중 것은 조용히 끝낸다.
            return ChargeConverter.toStarted(payment, "");
        }

        PortOneClient.PortOnePayment actual = portOneClient.getPayment(paymentId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PAYMENT_NOT_COMPLETED));

        if (!actual.isPaid()) {
            // 아직 돈이 들어오지 않았다. 가상계좌 발급이나 대기 상태가 여기로 온다 —
            // 실패로 확정하지 않는다. 나중에 웹훅이 다시 온다.
            throw new ChargeException(ChargeErrorCode.PAYMENT_NOT_COMPLETED);
        }
        if (actual.totalAmount() != payment.getExpectedAmount()) {
            // 금액 조작을 여기서 막는다. 결제 시작 때 기록해 두지 않았다면 대조할 기준이 없다.
            payment.markFailed("결제 금액 불일치", LocalDateTime.now(TimeUtil.KST));
            log.error("결제 금액이 다릅니다. paymentId={}, 기대={}, 실제={}",
                    paymentId, payment.getExpectedAmount(), actual.totalAmount());
            throw new ChargeException(ChargeErrorCode.AMOUNT_MISMATCH);
        }

        // 상태 전이가 먼저다. 이 호출이 false 면 남이 이미 처리한 것이므로 지급하지 않는다.
        if (!payment.markPaid(actual.transactionId(), actual.totalAmount(),
                LocalDateTime.now(TimeUtil.KST))) {
            return ChargeConverter.toStarted(payment, "");
        }

        // 지급은 결제 시작 때 굳힌 값으로 한다. 상품을 다시 읽으면 그 사이 바뀐 값이 나온다.
        walletService.grant(new WalletCommand(
                        payment.getMember().getId(), payment.getRewardCurrency(),
                        WalletTransactionType.TOPUP,
                        "charge:" + payment.getId(), "CHARGE_PAYMENT", payment.getId()),
                payment.getRewardAmount(), payment.getBonusAmount());

        return ChargeConverter.toStarted(payment, "");
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
