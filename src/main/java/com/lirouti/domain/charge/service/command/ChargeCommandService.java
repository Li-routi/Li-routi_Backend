package com.lirouti.domain.charge.service.command;

import com.lirouti.domain.charge.client.PortOneClient;
import com.lirouti.domain.charge.converter.ChargeConverter;
import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.entity.ChargePayment;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.entity.ExchangeProduct;
import com.lirouti.domain.charge.enums.ChargePaymentStatus;
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
import com.lirouti.global.properties.PortOneProperties;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 재화 충전(결제 시작)과 교환.
 *
 * <p>결제를 시작해 대조 기준을 남기고, 포트원에 물어 실제로 돈이 들어왔는지 확인한 뒤 지급한다.
 * 재화끼리 바꾸는 것도 여기서 맡는다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 포트원 호출이 이 안에 있어서다 — 행 잠금을 쥔 채 외부
 * 응답을 기다리면 상대가 느린 만큼 우리 커넥션이 묶인다. 잠그고 고치는 일은
 * {@link ChargeSettlementCommandService} 가 자기 트랜잭션 안에서 한다.
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
    private final PortOneProperties portOneProperties;
    private final ChargeSettlementCommandService settlementCommandService;

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
        requireEnabled();
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

        return started(payment, orderNameOf(product));
    }

    /**
     * 결제를 검증하고 재화를 지급한다.
     *
     * <p><b>이 메서드에는 트랜잭션이 없다.</b> 포트원 조회가 중간에 있어서다 — 트랜잭션 안에서
     * 부르면 DB 커넥션과 행 잠금을 외부 왕복 시간만큼 붙잡는다(service_convention). DB 변경은
     * {@link ChargeSettlementCommandService} 가 각자의 트랜잭션으로 한다.
     *
     * <p>요청 본문의 값으로 판단하지 않는다. 결제 식별자로 <b>인증된 회원의</b> 행이 있는지
     * 먼저 보고, 없으면 포트원을 부르지도 않는다.
     */
    public ChargeResDTO.Settled complete(Long memberId, String paymentId) {
        settlementCommandService.requireOwnedBy(memberId, paymentId);
        return settle(paymentId, false);
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
    public void handleWebhook(String paymentId) {
        settle(paymentId, true);
    }

    /**
     * 검증과 지급. 완료 요청과 웹훅이 같은 길을 쓴다.
     *
     * <p>순서가 중요하다 — <b>외부 조회를 잠금 밖에서</b> 끝내고, 그 결과만 들고 잠금 안으로
     * 들어간다.
     */
    private ChargeResDTO.Settled settle(String paymentId, boolean fromWebhook) {
        Optional<ChargePaymentStatus> status = settlementCommandService.statusOf(paymentId);

        // 우리가 모르는 결제다. 웹훅 주소는 공개라 아무나 아무 식별자나 보낼 수 있으므로,
        // 여기서 끊지 않으면 그것만으로 우리가 포트원 API 를 대신 두들기게 된다.
        if (status.isEmpty()) {
            if (fromWebhook) {
                log.info("모르는 결제의 웹훅을 흘립니다. paymentId={}", paymentId);
                return null;
            }
            throw new ChargeException(ChargeErrorCode.PAYMENT_NOT_FOUND);
        }

        // 이미 끝난 결제로 포트원을 다시 부르지 않는다. 완료 요청과 웹훅이 둘 다 오는 것이
        // 정상이므로 이 경우가 드물지 않다.
        if (status.get() == ChargePaymentStatus.PAID) {
            return settlementCommandService.settledResult(paymentId);
        }
        // 실패도 종료 상태다. 다시 물어봐도 결과가 달라질 수 없다 — 웹훅에는 조용히 성공으로
        // 답해 재시도를 멈추고, 사람이 부른 완료 요청에는 이유를 알린다.
        if (status.get() == ChargePaymentStatus.FAILED) {
            if (fromWebhook) {
                return null;
            }
            throw new ChargeException(ChargeErrorCode.PAYMENT_ALREADY_FAILED);
        }

        PortOneClient.PortOnePayment actual = portOneClient.getPayment(paymentId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PAYMENT_NOT_COMPLETED));

        if (!actual.isPaid()) {
            // 아직 돈이 들어오지 않았다. 가상계좌 발급이나 대기 상태가 여기로 온다 —
            // 실패로 확정하지 않는다. 나중에 웹훅이 다시 온다.
            throw new ChargeException(ChargeErrorCode.PAYMENT_NOT_COMPLETED);
        }

        try {
            return settlementCommandService.apply(paymentId, actual);
        } catch (ChargeException e) {
            if (e.getCode() == ChargeErrorCode.AMOUNT_MISMATCH) {
                // 별도 트랜잭션으로 남긴다. apply 안에서 기록하면 그 예외가 기록까지 되돌린다.
                settlementCommandService.markFailed(paymentId, "결제 금액 불일치");
            }
            throw e;
        }
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
    /**
     * 충전을 받을 수 있는 상태인가.
     *
     * <p>끄면 <b>검증을 건너뛰는 것이 아니라 아예 받지 않는다.</b> AI 심사와 달리 fail-open 이
     * 아니다 — 검증 없이 지급하면 재화가 공짜가 된다.
     */
    /**
     * 충전 스위치. <b>새 결제만 막는다.</b>
     *
     * <p>지급(완료 요청·웹훅)에는 걸지 않는다. 스위치를 내리는 시점에도 <b>이미 시작된 결제는
     * 남아 있고</b>, 그 사람들은 곧 돈을 낸다. 지급까지 막으면 그 웹훅을 거절하게 되고,
     * 포트원은 몇 번 재시도하다 포기한다 — <b>돈은 빠져나갔는데 재화가 안 들어간다.</b>
     * 끄는 목적은 새로 받는 것을 멈추는 것이지, 이미 받은 돈을 떼먹는 것이 아니다.
     */
    private void requireEnabled() {
        if (!portOneProperties.isEnabled()) {
            throw new ChargeException(ChargeErrorCode.CHARGE_DISABLED);
        }
    }

    private String newPaymentId() {
        return "charge_" + UUID.randomUUID().toString().replace("-", "");
    }

    private ChargeResDTO.Started started(ChargePayment payment, String orderName) {
        return ChargeConverter.toStarted(payment, orderName,
                portOneProperties.getStoreId(), portOneProperties.getChannelKey());
    }

    /**
     * 주문명. <b>카드 명세서와 결제 내역에 찍힌다.</b>
     *
     * <p>재화 이름을 쓰지 않는다 — 화면 문구는 클라이언트가 정한다는 규칙 때문이기도 하고,
     * 명세서에 "GEM" 이 찍히면 사용자가 무엇을 샀는지 알아보기 어렵다.
     */
    private String orderNameOf(ChargeProduct product) {
        int total = product.getRewardAmount() + product.getBonusAmount();
        return "리루티 재화 충전 " + total;
    }
}
