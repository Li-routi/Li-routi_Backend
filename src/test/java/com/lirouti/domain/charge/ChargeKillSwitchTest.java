package com.lirouti.domain.charge;

import com.lirouti.domain.charge.client.PortOneClient;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.enums.ChargePaymentStatus;
import com.lirouti.domain.charge.exception.ChargeException;
import com.lirouti.domain.charge.exception.code.error.ChargeErrorCode;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 충전 스위치를 내렸을 때 무엇이 멈추고 무엇이 계속 도는가.
 *
 * <p>스위치는 <b>새 결제만</b> 막는다. 내리는 시점에도 이미 시작된 결제가 남아 있고 그 사람들은
 * 곧 돈을 낸다 — 지급까지 막으면 그 웹훅을 거절하게 되고, 포트원이 재시도를 포기한 뒤에는
 * <b>돈은 빠져나갔는데 재화가 없는</b> 상태로 굳는다.
 *
 * <p>트랜잭션을 걸지 않는다. 지급은 별도 트랜잭션에서 커밋되므로 테스트 트랜잭션 안에서는
 * 결과를 볼 수 없다.
 */
@SpringBootTest
@TestPropertySource(properties = "portone.enabled=false")
@DisplayName("충전 스위치")
class ChargeKillSwitchTest {

    @Autowired private ChargeCommandService chargeCommandService;
    @Autowired private ChargePaymentRepository chargePaymentRepository;
    @Autowired private ChargeProductRepository chargeProductRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberWalletRepository memberWalletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @MockitoBean private PortOneClient portOneClient;

    private Long memberId;
    private Long productId;

    @BeforeEach
    void setUp() {
        String tag = "ks-" + System.nanoTime();
        memberId = memberRepository.save(Member.builder()
                .email(tag + "@ex.com").nickname(tag)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag).build()).getId();
        productId = chargeProductRepository.save(ChargeProduct.builder()
                .rewardCurrency(Currency.GEM).rewardAmount(500).bonusAmount(50)
                .priceKrw(5500).popular(false).sortOrder(1).active(true).build()).getId();
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
    @DisplayName("스위치를 내리면 새 결제를 시작할 수 없다")
    void disabled_BlocksNewPayment() {
        assertThatThrownBy(() -> chargeCommandService.startCharge(memberId, productId))
                .isInstanceOf(ChargeException.class)
                .hasFieldOrPropertyWithValue("code", ChargeErrorCode.CHARGE_DISABLED);
    }

    @Test
    @DisplayName("스위치를 내려도 이미 낸 돈은 지급된다 — 막으면 돈만 가져간 꼴이 된다")
    void disabled_StillSettlesMoneyAlreadyTaken() {
        // 스위치를 내리기 "전에" 시작된 결제다. 스위치가 켜져 있어야 만들 수 있으므로
        // 시작 단계만 직접 만든다.
        String paymentId = startedBeforeSwitchWentDown();

        when(portOneClient.getPayment(paymentId)).thenReturn(Optional.of(
                new PortOneClient.PortOnePayment(paymentId, "PAID", "tx-ks",
                        new PortOneClient.PortOnePayment.Amount(5500),
                        OffsetDateTime.now().toString())));

        chargeCommandService.handleWebhook(paymentId);

        assertThat(chargePaymentRepository.findByPaymentIdForRead(paymentId).orElseThrow()
                .getStatus()).as("돈이 들어왔으니 지급까지 끝나야 한다")
                .isEqualTo(ChargePaymentStatus.PAID);
        assertThat(memberWalletRepository.findByMemberIdAndCurrency(memberId, Currency.GEM)
                .orElseThrow().totalBalance()).isEqualTo(550);
    }

    private String startedBeforeSwitchWentDown() {
        ChargeProduct product = chargeProductRepository.findById(productId).orElseThrow();
        return chargePaymentRepository.save(com.lirouti.domain.charge.entity.ChargePayment.builder()
                .member(memberRepository.findById(memberId).orElseThrow())
                .product(product)
                .paymentId("charge_" + java.util.UUID.randomUUID().toString().replace("-", ""))
                .requestedAt(java.time.LocalDateTime.now())
                .build()).getPaymentId();
    }
}
