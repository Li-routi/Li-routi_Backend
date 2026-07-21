package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;

/**
 * 재참여 동시성 테스트. @Transactional을 쓰지 않는다 — 두 스레드가 각자 트랜잭션으로
 * 커밋된 같은 행을 두고 경합해야 하므로, 셋업 데이터를 실제로 커밋하고 뒤에서 정리한다.
 */
@SpringBootTest
@DisplayName("챌린지 재참여 동시성 테스트")
class ChallengeParticipationConcurrencyTest {

    @Autowired
    private ChallengeCommandService challengeCommandService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private MemberChallengeRepository memberChallengeRepository;

    private Long memberId;
    private Long challengeId;

    @BeforeEach
    void setUp() {
        Member m = memberRepository.save(Member.builder()
                .email("conc@ex.com").nickname("conc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("conc-sid").build());
        Challenge c = challengeRepository.save(Challenge.builder()
                .name("conc챌린지").category(ChallengeCategory.HEALTH).active(true).build());
        // 예전에 그만둔 상태(active=false, 회차 1)로 커밋해 둔다.
        memberChallengeRepository.save(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(false).build());
        memberId = m.getId();
        challengeId = c.getId();
    }

    @AfterEach
    void tearDown() {
        memberChallengeRepository.findByMemberIdAndChallengeId(memberId, challengeId)
                .ifPresent(memberChallengeRepository::delete);
        challengeRepository.deleteById(challengeId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("동시에 재참여하면 하나만 성공하고 다른 하나는 409(ALREADY_PARTICIPATING)")
    void concurrentRejoin_OnlyOneSucceeds() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyParticipating = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        Runnable task = () -> {
            try {
                ready.countDown();       // 게이트 앞 도착 알림
                start.await();
                challengeCommandService.participate(memberId, challengeId);
                success.incrementAndGet();
            } catch (ChallengeException e) {
                if (e.getCode() == ChallengeErrorCode.ALREADY_PARTICIPATING) {
                    alreadyParticipating.incrementAndGet();
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
        };

        pool.submit(task);
        pool.submit(task);
        // 두 스레드가 모두 게이트 앞에 도착한 뒤에 출발시킨다(직렬 실행으로 새는 것 방지).
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();                       // 두 스레드 동시 출발
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(1);              // 정확히 하나만 성공
        assertThat(alreadyParticipating.get()).isEqualTo(1); // 다른 하나는 409
        assertThat(other.get()).isZero();                    // 예상 밖 예외 없음

        // 최종 상태: 참여 중, 회차는 2(한 번만 올라감 — 이중 증가 없음)
        MemberChallenge reloaded = memberChallengeRepository
                .findByMemberIdAndChallengeId(memberId, challengeId).orElseThrow();
        assertThat(reloaded.isParticipating()).isTrue();
        assertThat(reloaded.getParticipationRound()).isEqualTo(2);
    }
}
