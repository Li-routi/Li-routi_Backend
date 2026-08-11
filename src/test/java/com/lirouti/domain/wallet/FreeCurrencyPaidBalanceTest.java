package com.lirouti.domain.wallet;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.exception.WalletException;
import com.lirouti.domain.wallet.exception.code.error.WalletErrorCode;
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
 * 무료 재화에는 유상 잔액이 생기지 않는다.
 *
 * <p><b>리워드 회수가 무료 재화에서 일어난다.</b> 그 재화에 유상 잔액이 생기면 회수가 환불
 * 대상 재화를 깎기 시작한다 — 무상분을 다 쓴 사용자가 글을 지우면 현금으로 산 몫에서 빠진다.
 *
 * <p>지금은 그런 값을 만드는 코드가 없어서 안전할 뿐이다. <b>교환을 만들며 "결제한 것이니
 * 유상으로 기록하자" 는 판단이 나오면 그 순간 열린다.</b> 그 판단이 코드로 들어오는 시점에
 * 이 테스트가 먼저 빨간불을 낸다.
 */
@SpringBootTest
@Transactional
@DisplayName("무료 재화의 유상 잔액 차단")
class FreeCurrencyPaidBalanceTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private MemberWalletRepository memberWalletRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("fcp" + n + "@ex.com").nickname("fcp" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("fcp-sid-" + n).build();
        em.persist(me);
        em.flush();
    }

    @Test
    @DisplayName("무료 재화에 유상으로 지급하면 거절한다")
    void grant_RejectsPaidOnFreeCurrency() {
        assertThatThrownBy(() -> walletService.grant(new WalletCommand(
                me.getId(), Currency.TOPAZ, WalletTransactionType.TOPUP,
                "free-paid-1", null, null), 500, 0))
                .isInstanceOf(WalletException.class)
                .satisfies(e -> assertThat(((WalletException) e).getCode())
                        .isEqualTo(WalletErrorCode.PAID_BALANCE_NOT_ALLOWED));

        assertThat(memberWalletRepository.findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ))
                .as("거절됐으므로 지갑도 만들어지지 않는다").isEmpty();
    }

    @Test
    @DisplayName("유상·무상을 섞어 줘도 유상분이 있으면 거절한다 — 충전 보너스 모양으로도 못 만든다")
    void grant_RejectsMixedPaidOnFreeCurrency() {
        assertThatThrownBy(() -> walletService.grant(new WalletCommand(
                me.getId(), Currency.TOPAZ, WalletTransactionType.TOPUP,
                "free-paid-2", null, null), 500, 50))
                .isInstanceOf(WalletException.class);
    }

    @Test
    @DisplayName("무료 재화를 무상으로 주는 것은 정상이다")
    void grant_AllowsFreeOnFreeCurrency() {
        walletService.grant(new WalletCommand(
                me.getId(), Currency.TOPAZ, WalletTransactionType.CHALLENGE_REWARD,
                "free-free-1", null, null), 0, 300);
        em.flush();

        MemberWallet wallet = memberWalletRepository
                .findByMemberIdAndCurrency(me.getId(), Currency.TOPAZ).orElseThrow();

        assertAll(
                () -> assertThat(wallet.getFreeBalance()).isEqualTo(300),
                () -> assertThat(wallet.getPaidBalance()).isZero()
        );
    }

    @Test
    @DisplayName("유료 재화에는 유상 잔액이 정상이다 — 충전이 여기서 일어난다")
    void grant_AllowsPaidOnPaidCurrency() {
        walletService.grant(new WalletCommand(
                me.getId(), Currency.GEM, WalletTransactionType.TOPUP,
                "paid-paid-1", null, null), 500, 50);
        em.flush();

        MemberWallet wallet = memberWalletRepository
                .findByMemberIdAndCurrency(me.getId(), Currency.GEM).orElseThrow();

        assertAll(
                () -> assertThat(wallet.getPaidBalance()).as("현금으로 산 몫").isEqualTo(500),
                () -> assertThat(wallet.getFreeBalance()).as("충전 보너스").isEqualTo(50)
        );
    }

    @Test
    @DisplayName("엔티티도 스스로 막는다 — 지갑을 직접 다뤄도 뚫리지 않는다")
    void entity_RejectsPaidOnFreeCurrency() {
        MemberWallet topaz = MemberWallet.builder().member(me).currency(Currency.TOPAZ).build();

        assertThatThrownBy(() -> topaz.grant(100, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("무료 재화");
    }

    @Test
    @DisplayName("재화의 성질이 enum 에 적혀 있다 — 뒤집히면 여기부터 고친다")
    void currency_DeclaresWhetherPaidBalanceIsAllowed() {
        assertAll(
                () -> assertThat(Currency.GEM.isPaidBalanceAllowed())
                        .as("유료 재화 — 현금으로 산다").isTrue(),
                () -> assertThat(Currency.TOPAZ.isPaidBalanceAllowed())
                        .as("무료 재화 — 챌린지로 번다").isFalse()
        );
    }
}
