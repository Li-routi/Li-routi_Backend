package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.MemberRoutineStreak;
import com.lirouti.domain.achievement.repository.MemberRoutineStreakRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("회원 루틴 스트릭 동시성 테스트")
class MemberRoutineStreakCommandServiceConcurrencyTest {

    @Autowired
    private MemberRoutineStreakCommandService streakCommandService;
    @Autowired
    private MemberRoutineStreakRepository streakRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("개인 루틴과 그룹 루틴의 동시 최초 완료도 스트릭 행을 하나만 만든다")
    void recordCompletion_FirstMemberAndGroupRoutineCompletion_CreatesOneStreakRow() throws InterruptedException {
        Member member = memberRepository.save(Member.builder()
                .email("streak-concurrency-" + System.nanoTime() + "@example.com")
                .nickname("streak-concurrency")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("streak-concurrency-" + System.nanoTime())
                .build());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        LocalDate completedDate = LocalDate.of(2026, 8, 13);
        LocalDateTime occurredAt = completedDate.atTime(9, 0);

        Runnable memberRoutineTask = task(
                tx, ready, start, done, success, unexpected,
                member.getId(), completedDate, occurredAt,
                "MEMBER_ROUTINE_VERIFICATION", 1L);
        Runnable groupRoutineTask = task(
                tx, ready, start, done, success, unexpected,
                member.getId(), completedDate, occurredAt,
                "GROUP_ROUTINE_VERIFICATION", 2L);

        pool.submit(memberRoutineTask);
        pool.submit(groupRoutineTask);
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(2);
        assertThat(unexpected.get()).isZero();
        assertThat(streakRepository.findAll())
                .filteredOn(streak -> streak.getMemberId().equals(member.getId()))
                .hasSize(1)
                .first()
                .extracting(MemberRoutineStreak::getCurrentStreak)
                .isEqualTo(1);
    }

    private Runnable task(
            TransactionTemplate tx,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            AtomicInteger success,
            AtomicInteger unexpected,
            Long memberId,
            LocalDate completedDate,
            LocalDateTime occurredAt,
            String sourceType,
            Long sourceId
    ) {
        return () -> {
            try {
                ready.countDown();
                start.await();
                tx.executeWithoutResult(status -> streakCommandService.recordCompletion(
                        memberId, completedDate, occurredAt, sourceType, sourceId));
                success.incrementAndGet();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                unexpected.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
    }
}
