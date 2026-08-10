package com.lirouti.domain.wallet.service;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.exception.WalletException;
import com.lirouti.domain.wallet.exception.code.error.WalletErrorCode;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.repository.WalletTransactionRepository;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 잔액 동시성.
 *
 * <p><b>잔액은 유니크 제약으로 막을 수 없다.</b> 이 저장소는 "애플리케이션에서 검사하고 저장"
 * 사이에 동시 요청이 모두 통과하는 것을 이미 겪었다(챌린지 재참여·인증). 그것이 잔액에서
 * 벌어지면 <b>잔액이 음수가 되거나 같은 재화를 두 번 쓴다.</b>
 *
 * <p>{@code @Transactional} 을 쓰지 않는다 — 스레드들이 각자 트랜잭션으로 커밋된 같은 행을
 * 두고 경합해야 하므로, 셋업 데이터를 실제로 커밋하고 뒤에서 정리한다.
 */
@SpringBootTest
@DisplayName("재화 지갑 동시성 테스트")
class WalletConcurrencyTest {

    private static final int INITIAL_BALANCE = 100;
    private static final int SPEND = 30;
    private static final int THREADS = 10;

    @Autowired
    private WalletService walletService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberWalletRepository memberWalletRepository;
    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        Member m = memberRepository.save(Member.builder()
                .email("walletconc@ex.com").nickname("walletconc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("walletconc-sid").build());
        memberId = m.getId();
        walletService.grant(new WalletCommand(memberId, Currency.GEM,
                WalletTransactionType.TOPUP, "conc-seed", null, null), 0, INITIAL_BALANCE);
    }

    @AfterEach
    void tearDown() {
        walletTransactionRepository.deleteAll(
                walletTransactionRepository.findAll().stream()
                        .filter(t -> t.getMember().getId().equals(memberId))
                        .toList());
        memberWalletRepository.findByMemberIdAndCurrency(memberId, Currency.GEM)
                .ifPresent(memberWalletRepository::delete);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("동시에 차감해도 잔액보다 많이 나가지 않는다 — 음수가 되는 경로가 없다")
    void concurrentDeduct_NeverOverspends() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            // 멱등 키를 스레드마다 다르게 준다. 같은 키면 멱등 처리가 경합을 대신 막아 버려
            // 정작 보려는 것(잠금)이 검증되지 않는다.
            String key = "conc-spend-" + i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    walletService.deduct(new WalletCommand(memberId, Currency.GEM,
                            WalletTransactionType.PURCHASE, key, null, null), SPEND);
                    success.incrementAndGet();
                } catch (WalletException e) {
                    if (e.getCode() == WalletErrorCode.INSUFFICIENT_BALANCE) {
                        insufficient.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        MemberWallet wallet = memberWalletRepository
                .findByMemberIdAndCurrency(memberId, Currency.GEM).orElseThrow();
        int expectedSuccess = INITIAL_BALANCE / SPEND;   // 100 / 30 = 3

        assertAll(
                () -> assertThat(workersReady).isTrue(),
                () -> assertThat(finished).as("잠금 대기가 풀리지 않으면 여기서 걸린다").isTrue(),
                () -> assertThat(other.get()).as("예상 밖 예외가 없어야 한다").isZero(),
                () -> assertThat(success.get())
                        .as("100 으로 30 짜리는 세 번만 살 수 있다").isEqualTo(expectedSuccess),
                () -> assertThat(insufficient.get()).isEqualTo(THREADS - expectedSuccess),
                () -> assertThat(wallet.totalBalance())
                        .as("남은 잔액은 10 이고, 무엇보다 음수가 아니어야 한다")
                        .isEqualTo(INITIAL_BALANCE - expectedSuccess * SPEND),
                () -> assertThat(wallet.totalBalance()).isNotNegative()
        );
    }

    @Test
    @DisplayName("같은 멱등 키로 동시에 들어와도 한 번만 반영된다")
    void concurrentSameKey_AppliedOnce() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    walletService.deduct(new WalletCommand(memberId, Currency.GEM,
                            WalletTransactionType.PURCHASE, "conc-same-key", null, null), SPEND);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        MemberWallet wallet = memberWalletRepository
                .findByMemberIdAndCurrency(memberId, Currency.GEM).orElseThrow();

        assertAll(
                () -> assertThat(workersReady).isTrue(),
                () -> assertThat(finished).isTrue(),
                () -> assertThat(wallet.totalBalance())
                        .as("재시도가 겹쳐도 한 번만 빠져야 한다")
                        .isEqualTo(INITIAL_BALANCE - SPEND),
                () -> assertThat(failed.get())
                        .as("멱등 처리는 예외가 아니라 같은 결과를 돌려주는 것이다").isZero()
        );
    }
}
