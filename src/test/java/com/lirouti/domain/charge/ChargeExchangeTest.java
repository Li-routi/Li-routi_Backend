package com.lirouti.domain.charge;

import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.entity.ChargePayment;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.entity.ExchangeProduct;
import com.lirouti.domain.charge.enums.ChargePaymentStatus;
import com.lirouti.domain.charge.exception.ChargeException;
import com.lirouti.domain.charge.repository.ChargePaymentRepository;
import com.lirouti.domain.charge.service.command.ChargeCommandService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.exception.WalletException;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.service.WalletService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 재화 충전(결제 시작)과 교환.
 *
 * <p><b>검증·지급은 아직 없다</b> — 포트원 자격증명을 받은 뒤에 붙인다. 여기서는 그 앞 단계,
 * 즉 <b>대조 기준을 남기는 것</b>과 <b>재화끼리 바꾸는 것</b>을 본다.
 */
@SpringBootTest
@Transactional
@DisplayName("재화 충전·교환")
class ChargeExchangeTest {

    @Autowired
    private ChargeCommandService chargeCommandService;
    @Autowired
    private ChargePaymentRepository chargePaymentRepository;
    @Autowired
    private MemberWalletRepository memberWalletRepository;
    @Autowired
    private WalletService walletService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private ChargeProduct product;      // GEM 500 + 보너스 50, 5,500원
    private ExchangeProduct exchange;   // GEM 3 → TOPAZ 100

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("chg" + n + "@ex.com").nickname("chg" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("chg-sid-" + n).build();
        em.persist(me);

        product = ChargeProduct.builder()
                .rewardCurrency(Currency.GEM).rewardAmount(500).bonusAmount(50)
                .priceKrw(5500).popular(true).sortOrder(1).active(true).build();
        em.persist(product);

        exchange = ExchangeProduct.builder()
                .fromCurrency(Currency.GEM).fromAmount(3)
                .toCurrency(Currency.TOPAZ).toAmount(100)
                .sortOrder(1).active(true).build();
        em.persist(exchange);
        em.flush();
    }

    private int balance(Currency currency) {
        return memberWalletRepository.findByMemberIdAndCurrency(me.getId(), currency)
                .map(w -> w.totalBalance()).orElse(0);
    }

    private void giveGem(int paid, int free) {
        walletService.grant(new WalletCommand(me.getId(), Currency.GEM,
                WalletTransactionType.TOPUP, "seed-gem-" + seq.get(), null, null), paid, free);
        em.flush();
    }

    // ── 결제 시작 ──

    @Test
    @DisplayName("결제를 시작하면 대조 기준이 남는다 — 금액과 지급 내용이 굳는다")
    void start_RecordsExpectedAmountAndSnapshot() {
        ChargeResDTO.Started started =
                chargeCommandService.startCharge(me.getId(), product.getId());
        em.flush();

        ChargePayment saved = chargePaymentRepository
                .findByPaymentIdAndMemberId(started.paymentId(), me.getId()).orElseThrow();

        assertAll(
                () -> assertThat(saved.getStatus()).isEqualTo(ChargePaymentStatus.READY),
                () -> assertThat(saved.getExpectedAmount()).isEqualTo(5500),
                () -> assertThat(saved.getRewardCurrency()).isEqualTo(Currency.GEM),
                () -> assertThat(saved.getRewardAmount()).isEqualTo(500),
                () -> assertThat(saved.getBonusAmount()).isEqualTo(50),
                () -> assertThat(saved.getTxId()).as("결제 전에는 포트원 거래가 없다").isNull()
        );
    }

    @Test
    @DisplayName("결제를 시작해도 재화는 들어오지 않는다 — 돈이 아직 안 오갔다")
    void start_DoesNotGrantYet() {
        chargeCommandService.startCharge(me.getId(), product.getId());
        em.flush();

        assertThat(balance(Currency.GEM)).isZero();
    }

    @Test
    @DisplayName("결제를 시작한 뒤 상품이 바뀌어도 시작 시점 값으로 남는다")
    void start_SnapshotSurvivesProductChange() {
        ChargeResDTO.Started started =
                chargeCommandService.startCharge(me.getId(), product.getId());
        em.flush();

        // 운영이 지급 수량을 줄였다. 이미 시작된 결제는 영향을 받으면 안 된다 —
        // 금액 검증은 expected_amount 로 하니 통과하는데 지급만 줄어드는 상황이 생긴다.
        em.createQuery("update ChargeProduct p set p.rewardAmount = 300 where p.id = :id")
                .setParameter("id", product.getId()).executeUpdate();
        em.clear();

        ChargePayment saved = chargePaymentRepository
                .findByPaymentIdAndMemberId(started.paymentId(), me.getId()).orElseThrow();

        assertThat(saved.getRewardAmount())
                .as("상품이 300 으로 바뀌어도 결제는 500 을 지급해야 한다").isEqualTo(500);
    }

    @Test
    @DisplayName("판매가 종료된 상품은 결제를 시작할 수 없다 — 목록에 없어도 id 로 부를 수 있다")
    void start_RejectsInactiveProduct() {
        em.createQuery("update ChargeProduct p set p.active = false where p.id = :id")
                .setParameter("id", product.getId()).executeUpdate();
        em.clear();

        assertThatThrownBy(() -> chargeCommandService.startCharge(me.getId(), product.getId()))
                .isInstanceOf(ChargeException.class);
    }

    @Test
    @DisplayName("결제 식별자는 매번 다르다 — 순번이면 남의 결제 건수가 새어 나간다")
    void start_PaymentIdIsUnguessable() {
        ChargeResDTO.Started a = chargeCommandService.startCharge(me.getId(), product.getId());
        ChargeResDTO.Started b = chargeCommandService.startCharge(me.getId(), product.getId());
        em.flush();

        assertAll(
                () -> assertThat(a.paymentId()).isNotEqualTo(b.paymentId()),
                // 모양으로 본다. 처음에는 "회원 id 를 포함하지 않는다" 로 썼는데, 32자리
                // 16진수는 한 자리 숫자를 거의 항상 포함하므로 회원 id 가 작은 환경(새 DB)
                // 에서만 깨졌다 — 로컬은 id 가 커서 통과했다. 검증하려던 것은 "순번이
                // 아니다" 이므로 형식을 보는 것이 맞다.
                () -> assertThat(a.paymentId()).matches("charge_[0-9a-f]{32}")
        );
    }

    // ── 교환 ──

    @Test
    @DisplayName("교환하면 낸 재화가 빠지고 받은 재화가 무상으로 들어온다")
    void exchange_DeductsAndGrantsFree() {
        giveGem(10, 0);

        ChargeResDTO.ExchangeResult result =
                chargeCommandService.exchange(me.getId(), exchange.getId(), "k1");
        em.flush();

        assertAll(
                () -> assertThat(balance(Currency.GEM)).isEqualTo(7),
                () -> assertThat(balance(Currency.TOPAZ)).isEqualTo(100),
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ).orElseThrow()
                        .getPaidBalance())
                        .as("무료 재화라 유상 잔액이 생기지 않는다").isZero(),
                () -> assertThat(result.toAmount()).isEqualTo(100)
        );
    }

    @Test
    @DisplayName("잔액이 모자라면 교환이 통째로 막힌다 — 받는 쪽도 안 생긴다")
    void exchange_RejectsWhenInsufficient() {
        giveGem(2, 0);   // 3 이 필요하다

        assertThatThrownBy(() -> chargeCommandService.exchange(me.getId(), exchange.getId(), "k1"))
                .isInstanceOf(WalletException.class);

        assertAll(
                () -> assertThat(balance(Currency.GEM)).isEqualTo(2),
                () -> assertThat(memberWalletRepository
                        .findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ))
                        .as("차감이 먼저라 받는 쪽 지갑이 만들어지지 않는다").isEmpty()
        );
    }

    @Test
    @DisplayName("같은 상품을 여러 번 교환할 수 있다 — 멱등 키가 두 번째를 삼키면 안 된다")
    void exchange_CanRepeatSameProduct() {
        giveGem(10, 0);

        chargeCommandService.exchange(me.getId(), exchange.getId(), "k1");
        chargeCommandService.exchange(me.getId(), exchange.getId(), "k2");
        em.flush();

        assertAll(
                () -> assertThat(balance(Currency.GEM)).isEqualTo(4),
                () -> assertThat(balance(Currency.TOPAZ)).isEqualTo(200)
        );
    }

    @Test
    @DisplayName("같은 키로 다시 보내면 두 번 차감되지 않는다 — 응답이 유실된 재시도다")
    void exchange_SameKeyIsNotChargedTwice() {
        giveGem(10, 0);

        chargeCommandService.exchange(me.getId(), exchange.getId(), "retry-1");
        // 클라이언트가 응답을 못 받아 같은 키로 다시 보낸다.
        chargeCommandService.exchange(me.getId(), exchange.getId(), "retry-1");
        em.flush();

        assertAll(
                () -> assertThat(balance(Currency.GEM))
                        .as("한 번만 빠져야 한다").isEqualTo(7),
                () -> assertThat(balance(Currency.TOPAZ))
                        .as("한 번만 들어와야 한다").isEqualTo(100)
        );
    }

    // ── 상품 불변식 ──

    @Test
    @DisplayName("무료 재화를 현금으로 파는 상품은 만들 수 없다 — 결제만 되고 지급이 실패한다")
    void chargeProduct_RejectsFreeCurrency() {
        assertThatThrownBy(() -> ChargeProduct.builder()
                .rewardCurrency(Currency.TOPAZ).rewardAmount(100).bonusAmount(0)
                .priceKrw(1100).popular(false).sortOrder(1).active(true).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("무료 재화");
    }

    @Test
    @DisplayName("같은 재화끼리 교환하는 상품은 만들 수 없다")
    void exchangeProduct_RejectsSameCurrency() {
        assertThatThrownBy(() -> ExchangeProduct.builder()
                .fromCurrency(Currency.GEM).fromAmount(3)
                .toCurrency(Currency.GEM).toAmount(100)
                .sortOrder(1).active(true).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
