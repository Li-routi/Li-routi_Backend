package com.lirouti.domain.wallet.service;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.wallet.dto.response.WalletResDTO;
import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.entity.WalletTransaction;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.exception.WalletException;
import com.lirouti.domain.wallet.exception.code.error.WalletErrorCode;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.repository.WalletTransactionRepository;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import com.lirouti.domain.wallet.service.query.WalletQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * 지갑의 셈법.
 *
 * <p><b>여기서 틀리면 돈이 틀린다.</b> 그래서 "잔액이 얼마가 되었나"만 보지 않고 <b>원장에
 * 무엇이 남았나</b>를 함께 본다. 둘이 갈리면 나중에 어느 쪽이 진실인지 알 수 없게 된다.
 *
 * <p>동시성은 여기서 볼 수 없다 — 트랜잭션 하나 안에서 도는 테스트라 경합이 일어나지 않는다.
 * 그쪽은 {@code WalletConcurrencyTest} 에 있다.
 */
@SpringBootTest
@Transactional
@DisplayName("재화 지갑 — 잔액과 원장")
class WalletLedgerTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletQueryService walletQueryService;
    @Autowired
    private MemberWalletRepository memberWalletRepository;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("wallet" + n + "@ex.com").nickname("wallet" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("wallet-sid-" + n).build();
        em.persist(m);
        em.flush();
        return m;
    }

    private WalletCommand command(Member m, WalletTransactionType type, String key) {
        return new WalletCommand(m.getId(), Currency.GEM, type, key, null, null);
    }

    private MemberWallet walletOf(Member m) {
        return memberWalletRepository.findByMemberIdAndCurrency(m.getId(), Currency.GEM)
                .orElseThrow();
    }

    // ── 조회 ──

    @Test
    @DisplayName("한 번도 받은 적 없으면 모든 재화가 0으로 나간다 — 빠지지 않는다")
    void balancesAreZeroWhenNoWalletRow() {
        Member m = member();

        WalletResDTO.Balances balances = walletQueryService.getBalances(m.getId());

        assertAll(
                () -> assertThat(balances.balances())
                        .as("재화 종류마다 한 건씩 항상 실린다")
                        .hasSize(Currency.values().length),
                () -> assertThat(balances.balances())
                        .extracting(WalletResDTO.Balance::balance)
                        .containsOnly(0),
                () -> assertThat(balances.balances())
                        .extracting(WalletResDTO.Balance::currency)
                        .containsExactlyInAnyOrder(Currency.values()),
                () -> assertThat(memberWalletRepository.findAllByMemberId(m.getId()))
                        .as("조회가 지갑을 만들면 안 된다 — 열어 본 것만으로 빈 지갑이 쌓인다")
                        .isEmpty()
        );
    }

    // ── 지급 ──

    @Test
    @DisplayName("지급하면 잔액이 오르고 원장에 그 사실이 남는다")
    void grantIncreasesBalanceAndRecordsLedger() {
        Member m = member();

        WalletTransaction tx = walletService.grant(
                command(m, WalletTransactionType.TOPUP, "topup:1"), 500, 50);

        assertAll(
                () -> assertThat(walletOf(m).getPaidBalance()).isEqualTo(500),
                () -> assertThat(walletOf(m).getFreeBalance()).isEqualTo(50),
                () -> assertThat(walletOf(m).totalBalance()).isEqualTo(550),
                () -> assertThat(tx.getPaidDelta()).isEqualTo(500),
                () -> assertThat(tx.getFreeDelta()).isEqualTo(50),
                () -> assertThat(tx.getPaidBalanceAfter()).isEqualTo(500),
                () -> assertThat(tx.getFreeBalanceAfter()).isEqualTo(50)
        );
    }

    // ── 차감 ──

    @Test
    @DisplayName("차감은 무상부터 쓴다 — 유상 잔액을 남겨야 환불할 수 있다")
    void deductSpendsFreeBalanceFirst() {
        Member m = member();
        walletService.grant(command(m, WalletTransactionType.TOPUP, "seed:1"), 100, 30);

        walletService.deduct(command(m, WalletTransactionType.PURCHASE, "buy:1"), 20);

        assertAll(
                () -> assertThat(walletOf(m).getFreeBalance())
                        .as("무상 30 에서 20 이 빠진다").isEqualTo(10),
                () -> assertThat(walletOf(m).getPaidBalance())
                        .as("유상은 손대지 않는다").isEqualTo(100)
        );
    }

    @Test
    @DisplayName("무상이 모자라면 모자란 만큼만 유상에서 뺀다")
    void deductFallsBackToPaidBalance() {
        Member m = member();
        walletService.grant(command(m, WalletTransactionType.TOPUP, "seed:2"), 100, 30);

        WalletTransaction tx = walletService.deduct(
                command(m, WalletTransactionType.PURCHASE, "buy:2"), 50);

        assertAll(
                () -> assertThat(walletOf(m).getFreeBalance()).isZero(),
                () -> assertThat(walletOf(m).getPaidBalance())
                        .as("모자란 20 만 유상에서 뺀다").isEqualTo(80),
                () -> assertThat(tx.getFreeDelta())
                        .as("원장에도 어느 쪽이 얼마나 빠졌는지 남는다").isEqualTo(-30),
                () -> assertThat(tx.getPaidDelta()).isEqualTo(-20)
        );
    }

    @Test
    @DisplayName("잔액이 모자라면 아무것도 바꾸지 않는다 — 부분 차감은 없다")
    void deductFailsWithoutTouchingBalance() {
        Member m = member();
        walletService.grant(command(m, WalletTransactionType.TOPUP, "seed:3"), 10, 5);
        long ledgerBefore = walletTransactionRepository.count();

        assertThatThrownBy(() -> walletService.deduct(
                command(m, WalletTransactionType.PURCHASE, "buy:3"), 100))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException) e).getCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);

        assertAll(
                () -> assertThat(walletOf(m).totalBalance())
                        .as("15 그대로여야 한다").isEqualTo(15),
                () -> assertThat(walletTransactionRepository.count())
                        .as("실패한 거래는 원장에 남지 않는다").isEqualTo(ledgerBefore)
        );
    }

    // ── 멱등 ──

    @Test
    @DisplayName("같은 멱등 키로 두 번 지급해도 한 번만 반영된다")
    void grantIsIdempotent() {
        Member m = member();
        long ledgerBefore = walletTransactionRepository.count();

        WalletTransaction first = walletService.grant(
                command(m, WalletTransactionType.CHALLENGE_REWARD, "reward:verification:1"), 0, 10);
        WalletTransaction second = walletService.grant(
                command(m, WalletTransactionType.CHALLENGE_REWARD, "reward:verification:1"), 0, 10);

        assertAll(
                () -> assertThat(walletOf(m).totalBalance())
                        .as("재시도로 두 번 지급되면 그대로 돈 문제가 된다").isEqualTo(10),
                () -> assertThat(second.getId())
                        .as("두 번째는 처음 거래를 그대로 돌려준다").isEqualTo(first.getId()),
                () -> assertThat(walletTransactionRepository.count()).isEqualTo(ledgerBefore + 1)
        );
    }

    @Test
    @DisplayName("같은 멱등 키로 두 번 차감해도 한 번만 빠진다")
    void deductIsIdempotent() {
        Member m = member();
        walletService.grant(command(m, WalletTransactionType.TOPUP, "seed:4"), 0, 100);

        walletService.deduct(command(m, WalletTransactionType.PURCHASE, "buy:item:7"), 30);
        walletService.deduct(command(m, WalletTransactionType.PURCHASE, "buy:item:7"), 30);

        assertThat(walletOf(m).totalBalance()).isEqualTo(70);
    }

    // ── 재화 분리 ──

    @Test
    @DisplayName("재화가 다르면 잔액이 서로 섞이지 않는다")
    void currenciesAreIndependent() {
        Member m = member();
        walletService.grant(new WalletCommand(m.getId(), Currency.GEM,
                WalletTransactionType.TOPUP, "gem:1", null, null), 100, 0);
        walletService.grant(new WalletCommand(m.getId(), Currency.TOPAZ,
                WalletTransactionType.EXCHANGE_IN, "topaz:1", null, null), 0, 300);

        WalletResDTO.Balances balances = walletQueryService.getBalances(m.getId());

        assertAll(
                () -> assertThat(balances.balances()).anySatisfy(b -> {
                    assertThat(b.currency()).isEqualTo(Currency.GEM);
                    assertThat(b.balance()).isEqualTo(100);
                }),
                () -> assertThat(balances.balances()).anySatisfy(b -> {
                    assertThat(b.currency()).isEqualTo(Currency.TOPAZ);
                    assertThat(b.balance()).isEqualTo(300);
                })
        );
    }
}
