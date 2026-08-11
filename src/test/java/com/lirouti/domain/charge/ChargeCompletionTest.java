package com.lirouti.domain.charge;

import com.lirouti.domain.charge.client.PortOneClient;
import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.entity.ChargePayment;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.enums.ChargePaymentStatus;
import com.lirouti.domain.charge.exception.ChargeException;
import com.lirouti.domain.charge.repository.ChargePaymentRepository;
import com.lirouti.domain.charge.service.command.ChargeCommandService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 결제 검증과 지급.
 *
 * <p><b>여기가 뚫리면 재화가 공짜가 된다.</b> 클라이언트가 "결제했다" 고 말하는 것만 믿으면
 * 위조한 요청으로 무한 충전이 된다. 그래서 <b>포트원의 답만</b> 근거로 삼는다.
 */
@SpringBootTest
@Transactional
@DisplayName("결제 검증·지급")
class ChargeCompletionTest {

    private static final int PRICE = 5500;
    private static final int REWARD = 500;
    private static final int BONUS = 50;

    @Autowired
    private ChargeCommandService chargeCommandService;
    @Autowired
    private ChargePaymentRepository chargePaymentRepository;
    @Autowired
    private MemberWalletRepository memberWalletRepository;

    @MockitoBean
    private PortOneClient portOneClient;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private Member other;
    private ChargeProduct product;
    private String paymentId;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = member("cpl" + n);
        other = member("cpo" + n);

        product = ChargeProduct.builder()
                .rewardCurrency(Currency.GEM).rewardAmount(REWARD).bonusAmount(BONUS)
                .priceKrw(PRICE).popular(false).sortOrder(1).active(true).build();
        em.persist(product);
        em.flush();

        paymentId = chargeCommandService.startCharge(me.getId(), product.getId()).paymentId();
        em.flush();
    }

    private Member member(String tag) {
        Member m = Member.builder()
                .email(tag + "@ex.com").nickname(tag)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag + "-sid").build();
        em.persist(m);
        return m;
    }

    /** 포트원이 이렇게 답한다고 두는 것. 이 답만이 지급의 근거다. */
    private void portOneSays(String status, int amount) {
        when(portOneClient.getPayment(any())).thenReturn(Optional.of(
                new PortOneClient.PortOnePayment(paymentId, status, "tx-" + paymentId,
                        new PortOneClient.PortOnePayment.Amount(amount), "2026-08-11T00:00:00Z")));
    }

    private int balance() {
        return memberWalletRepository.findByMemberIdAndCurrency(me.getId(), Currency.GEM)
                .map(w -> w.totalBalance()).orElse(0);
    }

    private ChargePayment stored() {
        return chargePaymentRepository.findByPaymentIdAndMemberId(paymentId, me.getId())
                .orElseThrow();
    }

    // ── 정상 ──

    @Test
    @DisplayName("검증을 통과하면 유상·무상이 나뉘어 들어온다")
    void complete_GrantsPaidAndFree() {
        portOneSays("PAID", PRICE);

        chargeCommandService.complete(me.getId(), paymentId);
        em.flush();

        assertAll(
                () -> assertThat(stored().getStatus()).isEqualTo(ChargePaymentStatus.PAID),
                () -> assertThat(stored().getTxId()).isNotBlank(),
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.GEM).orElseThrow()
                        .getPaidBalance()).as("결제분은 유상").isEqualTo(REWARD),
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.GEM).orElseThrow()
                        .getFreeBalance()).as("보너스는 무상").isEqualTo(BONUS)
        );
    }

    @Test
    @DisplayName("웹훅으로 들어와도 같은 처리를 한다 — 앱이 죽어도 재화는 들어온다")
    void webhook_GrantsToo() {
        portOneSays("PAID", PRICE);

        chargeCommandService.handleWebhook(paymentId);
        em.flush();

        assertAll(
                () -> assertThat(stored().getStatus()).isEqualTo(ChargePaymentStatus.PAID),
                () -> assertThat(balance()).isEqualTo(REWARD + BONUS)
        );
    }

    @Test
    @DisplayName("완료 요청과 웹훅이 둘 다 와도 한 번만 지급된다")
    void completeThenWebhook_GrantsOnce() {
        portOneSays("PAID", PRICE);

        chargeCommandService.complete(me.getId(), paymentId);
        chargeCommandService.handleWebhook(paymentId);
        em.flush();

        assertThat(balance())
                .as("둘 다 오는 것이 정상이다. 나중 것은 조용히 끝나야 한다")
                .isEqualTo(REWARD + BONUS);
    }

    // ── 막아야 하는 것 ──

    @Test
    @DisplayName("금액이 다르면 지급하지 않는다 — 100원 결제하고 11만원어치를 받는 길")
    void complete_RejectsAmountMismatch() {
        portOneSays("PAID", 100);   // 실제로는 100원만 냈다

        assertThatThrownBy(() -> chargeCommandService.complete(me.getId(), paymentId))
                .isInstanceOf(ChargeException.class);

        // 실패로 "확정" 되는지는 여기서 보지 않는다. 이 테스트는 @Transactional 이라
        // 바깥 트랜잭션에 참여하므로 별도 트랜잭션의 커밋을 검증할 수 없다 —
        // 여기서 FAILED 를 단언하면 통과하지만 그것은 같은 영속성 컨텍스트의 값일 뿐이다.
        // 영속성은 ChargeFailurePersistenceTest 가 트랜잭션 없이 본다.
        assertThat(balance()).as("지급이 일어나지 않는다").isZero();
    }

    @Test
    @DisplayName("남의 결제로는 부를 수 없다 — 식별자를 알아내도 내 계정을 못 채운다")
    void complete_RejectsOtherMembersPayment() {
        portOneSays("PAID", PRICE);

        assertThatThrownBy(() -> chargeCommandService.complete(other.getId(), paymentId))
                .isInstanceOf(ChargeException.class);

        assertAll(
                () -> assertThat(balance()).as("결제한 사람에게도 안 들어간다").isZero(),
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(other.getId(), Currency.GEM))
                        .as("부른 사람에게는 더더욱 안 들어간다").isEmpty()
        );
    }

    @Test
    @DisplayName("포트원이 모르는 결제면 지급하지 않는다 — 결제창을 열지 않고 부른 경우")
    void complete_RejectsUnknownPayment() {
        when(portOneClient.getPayment(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chargeCommandService.complete(me.getId(), paymentId))
                .isInstanceOf(ChargeException.class);

        assertThat(balance()).isZero();
    }

    @Test
    @DisplayName("아직 완료되지 않은 결제는 실패로 확정하지 않는다 — 나중에 웹훅이 다시 온다")
    void complete_PendingIsNotFinal() {
        portOneSays("VIRTUAL_ACCOUNT_ISSUED", PRICE);

        assertThatThrownBy(() -> chargeCommandService.complete(me.getId(), paymentId))
                .isInstanceOf(ChargeException.class);

        assertAll(
                () -> assertThat(balance()).isZero(),
                () -> assertThat(stored().getStatus())
                        .as("READY 로 남아야 나중에 웹훅이 처리할 수 있다")
                        .isEqualTo(ChargePaymentStatus.READY)
        );
    }
}
