package com.lirouti.domain.charge.service.command;

import com.lirouti.domain.charge.client.PortOneClient;
import com.lirouti.domain.charge.converter.ChargeConverter;
import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.entity.ChargePayment;
import com.lirouti.domain.charge.exception.ChargeException;
import com.lirouti.domain.charge.exception.code.error.ChargeErrorCode;
import com.lirouti.domain.charge.repository.ChargePaymentRepository;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
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

/**
 * 결제 검증의 <b>DB 변경만</b> 담당한다. 트랜잭션 경계가 이 클래스에 있다.
 *
 * <p>{@link ChargeCommandService} 에서 분리한 이유는 인증 재심사와 같다 — <b>포트원 조회를
 * 트랜잭션 안에서 하면 DB 커넥션과 행 잠금을 외부 왕복 시간만큼 붙잡는다</b>(service_convention).
 * 같은 클래스에서 메서드만 나누면 자기 호출이 프록시를 거치지 않아 트랜잭션이 걸리지 않으므로
 * 빈을 나눴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeSettlementCommandService {

    private final ChargePaymentRepository chargePaymentRepository;
    private final WalletService walletService;
    private final PortOneProperties portOneProperties;

    /**
     * <b>내 결제가 맞는지</b>만 본다. 포트원을 부르기 전에 걸러 남의 결제로 조회를 만들지 않는다.
     *
     * <p>회원을 안 보면 남의 식별자를 알아낸 사람이 자기 계정으로 완료를 불러
     * <b>남의 결제로 자기 재화를 채운다.</b>
     */
    @Transactional(readOnly = true)
    public void requireOwnedBy(Long memberId, String paymentId) {
        chargePaymentRepository.findByPaymentIdAndMemberId(paymentId, memberId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * 이미 지급이 끝났으면 그 결과를 돌려준다.
     *
     * <p>완료 요청과 웹훅이 <b>둘 다 오는 것이 정상</b>이다. 여기서 걸러야 이미 끝난 결제로
     * 포트원을 다시 부르지 않는다.
     */
    @Transactional(readOnly = true)
    public Optional<ChargeResDTO.Started> alreadySettled(String paymentId) {
        return chargePaymentRepository.findByPaymentIdForRead(paymentId)
                .filter(ChargePayment::isPaid)
                .map(this::toStarted);
    }

    /**
     * 이미 실패로 확정됐는가.
     *
     * <p><b>{@code PAID} 만 종료로 보면 안 된다.</b> 금액 불일치로 실패한 결제에 웹훅이 다시
     * 오면 매번 포트원을 조회하고 매번 같은 예외를 던진다 — 결과가 달라질 수 없는데도 그렇다.
     */
    @Transactional(readOnly = true)
    public boolean alreadyFailed(String paymentId) {
        return chargePaymentRepository.findByPaymentIdForRead(paymentId)
                .map(ChargePayment::isFailed)
                .orElse(false);
    }

    /**
     * 검증 결과를 적용한다. <b>여기서만 잠근다.</b>
     *
     * <p>포트원 조회는 이미 끝난 뒤이므로 잠금이 외부 왕복을 기다리지 않는다.
     *
     * <p>잠근 뒤 <b>상태를 다시 본다.</b> 조회하는 동안 웹훅이 먼저 처리했을 수 있다.
     */
    @Transactional
    public ChargeResDTO.Started apply(String paymentId, PortOneClient.PortOnePayment actual) {
        ChargePayment payment = chargePaymentRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new ChargeException(ChargeErrorCode.PAYMENT_NOT_FOUND));

        if (payment.isPaid()) {
            return toStarted(payment);
        }
        // 우리가 물어본 결제가 맞는지 확인한다. 조회에 우리 식별자를 넘기므로 어긋날 일이
        // 거의 없지만, 어긋났다면 그 답으로 지급해서는 안 된다.
        if (!paymentId.equals(actual.id())) {
            throw new ChargeException(ChargeErrorCode.PAYMENT_NOT_FOUND);
        }
        if (actual.totalAmount() != payment.getExpectedAmount()) {
            // 여기서 기록하지 않는다. 이 예외가 트랜잭션을 되돌리므로 기록이 함께 사라진다 —
            // 실측으로 확인했다. 실패 확정은 호출부가 별도 트랜잭션으로 남긴다.
            log.error("결제 금액이 다릅니다. paymentId={}, 기대={}, 실제={}",
                    paymentId, payment.getExpectedAmount(), actual.totalAmount());
            throw new ChargeException(ChargeErrorCode.AMOUNT_MISMATCH);
        }

        if (!payment.markPaid(actual.transactionId(), actual.totalAmount(),
                LocalDateTime.now(TimeUtil.KST))) {
            // 잠금을 기다리는 사이 남이 처리했다.
            return toStarted(payment);
        }

        // 지급은 결제 시작 때 굳힌 값으로 한다. 상품을 다시 읽으면 그 사이 바뀐 값이 나온다.
        walletService.grant(new WalletCommand(
                        payment.getMember().getId(), payment.getRewardCurrency(),
                        WalletTransactionType.TOPUP,
                        "charge:" + payment.getId(), "CHARGE_PAYMENT", payment.getId()),
                payment.getRewardAmount(), payment.getBonusAmount());

        return toStarted(payment);
    }

    /**
     * 실패로 확정한다. <b>이 트랜잭션은 예외 없이 끝나야 기록이 남는다.</b>
     *
     * <p>{@link #apply} 안에서 기록하고 예외를 던지면 <b>기록이 함께 롤백된다</b> — 그러면
     * 결제가 {@code READY} 로 남아 웹훅이 영원히 재시도하고, 매번 포트원을 부른다.
     */
    @Transactional
    public void markFailed(String paymentId, String reason) {
        chargePaymentRepository.findByPaymentIdForUpdate(paymentId)
                .ifPresent(p -> p.markFailed(reason, LocalDateTime.now(TimeUtil.KST)));
    }

    private ChargeResDTO.Started toStarted(ChargePayment payment) {
        return ChargeConverter.toStarted(payment, "",
                portOneProperties.getStoreId(), portOneProperties.getChannelKey());
    }
}
