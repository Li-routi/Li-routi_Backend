package com.lirouti.domain.charge;

import com.lirouti.domain.charge.client.PortOneClient;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.enums.ChargePaymentStatus;
import com.lirouti.domain.charge.repository.ChargePaymentRepository;
import com.lirouti.domain.charge.repository.ChargeProductRepository;
import com.lirouti.domain.charge.service.command.ChargeCommandService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 금액 불일치를 실패로 <b>확정</b>하는가.
 *
 * <p><b>{@code @Transactional} 을 쓰지 않는다.</b> 다른 결제 테스트는 바깥 트랜잭션에 참여해서
 * 실제 커밋·롤백 경계가 만들어지지 않는다 — 그래서 기록이 롤백되는 것을 <b>보지 못했다.</b>
 *
 * <p>실제로 그렇게 새어 있었다. 검증 안에서 {@code markFailed} 를 부르고 예외를 던졌는데,
 * 그 예외가 기록까지 되돌려 결제가 {@code READY} 로 남았다. 그러면 <b>웹훅이 영원히
 * 재시도하고 매번 포트원을 부른다.</b> 이 테스트가 그것을 잡는다.
 */
@SpringBootTest
@DisplayName("결제 실패 기록의 영속성")
class ChargeFailurePersistenceTest {

    @Autowired private ChargeCommandService chargeCommandService;
    @Autowired private ChargePaymentRepository chargePaymentRepository;
    @Autowired private ChargeProductRepository chargeProductRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private MemberWalletRepository memberWalletRepository;
    @MockitoBean private PortOneClient portOneClient;

    private Long memberId;
    private Long productId;
    private String paymentId;

    @BeforeEach
    void setUp() {
        String tag = "probe-" + System.nanoTime();
        memberId = memberRepository.save(Member.builder()
                .email(tag + "@ex.com").nickname(tag)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag).build()).getId();
        productId = chargeProductRepository.save(ChargeProduct.builder()
                .rewardCurrency(Currency.GEM).rewardAmount(500).bonusAmount(50)
                .priceKrw(5500).popular(false).sortOrder(1).active(true).build()).getId();
        paymentId = chargeCommandService.startCharge(memberId, productId).paymentId();
    }

    @AfterEach
    void tearDown() {
        chargePaymentRepository.deleteAll(chargePaymentRepository.findAll().stream()
                .filter(p -> p.getMember().getId().equals(memberId)).toList());
        walletTransactionRepository.deleteAll(walletTransactionRepository.findAll().stream()
                .filter(t -> t.getMember().getId().equals(memberId)).toList());
        memberWalletRepository.findAllByMemberId(memberId).forEach(memberWalletRepository::delete);
        chargeProductRepository.deleteById(productId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("금액이 다르면 FAILED 로 남아야 한다")
    void amountMismatch_PersistsFailed() {
        when(portOneClient.getPayment(any())).thenReturn(Optional.of(
                new PortOneClient.PortOnePayment(paymentId, "PAID", "tx-1",
                        new PortOneClient.PortOnePayment.Amount(100), "2026-08-11T00:00:00Z")));

        assertThatThrownBy(() -> chargeCommandService.complete(memberId, paymentId))
                .isInstanceOf(RuntimeException.class);

        assertThat(chargePaymentRepository.findByPaymentIdAndMemberId(paymentId, memberId)
                .orElseThrow().getStatus())
                .as("실패로 확정돼야 웹훅이 무한 재시도하지 않는다")
                .isEqualTo(ChargePaymentStatus.FAILED);
    }
}
