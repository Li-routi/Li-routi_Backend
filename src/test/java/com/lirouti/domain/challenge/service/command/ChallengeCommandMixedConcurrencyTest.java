package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;

/**
 * 서로 다른 명령이 같은 참여 행을 두고 경합하는 상황을 검증한다(#53).
 *
 * 기존 동시성 테스트는 같은 종류끼리만(인증 vs 인증, 재참여 vs 재참여) 경합시켜서
 * 인증이 락 없이 읽어 이탈 결과를 덮어쓰는 경로를 잡지 못했다.
 *
 * @Transactional을 쓰지 않는다 — 두 스레드가 각자 트랜잭션으로 커밋된 같은 행을 두고
 * 경합해야 하므로, 셋업 데이터를 실제로 커밋하고 뒤에서 정리한다.
 */
@SpringBootTest
@DisplayName("챌린지 명령 혼합 동시성 테스트")
class ChallengeCommandMixedConcurrencyTest {

    private static final String MEDIA_KEY =
            "challenge-verifications/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";

    @Autowired
    private ChallengeCommandService challengeCommandService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private MemberChallengeRepository memberChallengeRepository;
    @Autowired
    private ChallengeVerificationRepository challengeVerificationRepository;

    private Long memberId;
    private Long challengeId;

    @BeforeEach
    void setUp() {
        // email·social_id에 유니크 제약이 있다. @Transactional 없이 실제 커밋하므로,
        // 앞선 실행이 비정상 종료해 정리가 안 됐으면 고정값은 setUp 자체를 깨뜨린다.
        // 실행마다 유일한 값을 써서 남은 행과 부딪히지 않게 한다.
        String unique = UUID.randomUUID().toString();
        Member m = memberRepository.save(Member.builder()
                .email("mixed-" + unique + "@ex.com").nickname("mixed")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mixed-sid-" + unique).build());
        Challenge c = challengeRepository.save(Challenge.builder()
                .name("mixed챌린지").category(ChallengeCategory.HEALTH).active(true).build());
        // 참여 중(active=true, 회차 1, 아직 인증 없음)으로 커밋해 둔다.
        memberChallengeRepository.save(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());
        memberId = m.getId();
        challengeId = c.getId();
    }

    @AfterEach
    void tearDown() {
        memberChallengeRepository.findByMemberIdAndChallengeId(memberId, challengeId)
                .ifPresent(mc -> {
                    challengeVerificationRepository.deleteAll(
                            challengeVerificationRepository.findFeedByCursor(challengeId, null, 100));
                    memberChallengeRepository.delete(mc);
                });
        challengeRepository.deleteById(challengeId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("인증과 이탈이 동시에 들어와도 이탈이 되살아나지 않는다")
    void concurrentVerifyAndLeave_LeaveIsNotResurrected() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger verifySucceeded = new AtomicInteger();
        AtomicInteger verifyRejected = new AtomicInteger();
        AtomicInteger leaveSucceeded = new AtomicInteger();
        AtomicReference<RuntimeException> unexpected = new AtomicReference<>();

        Runnable verifyTask = () -> {
            try {
                ready.countDown();
                start.await();
                challengeCommandService.verify(
                        memberId, challengeId, new ChallengeReqDTO.Verify(MEDIA_KEY, "동시 인증"));
                verifySucceeded.incrementAndGet();
            } catch (ChallengeException e) {
                // 이탈이 먼저 커밋됐다면 참여 중이 아니므로 거절되는 것이 정상이다.
                if (e.getCode() == ChallengeErrorCode.NOT_PARTICIPATING) {
                    verifyRejected.incrementAndGet();
                } else {
                    unexpected.set(e);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                unexpected.set(e);
            } finally {
                done.countDown();
            }
        };

        Runnable leaveTask = () -> {
            try {
                ready.countDown();
                start.await();
                challengeCommandService.leave(memberId, challengeId);
                leaveSucceeded.incrementAndGet();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                unexpected.set(e);
            } finally {
                done.countDown();
            }
        };

        pool.submit(verifyTask);
        pool.submit(leaveTask);
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(unexpected.get()).isNull();

        // 이탈은 어느 순서로 실행되든 성공한다.
        // (이탈이 먼저면 그대로, 인증이 먼저여도 그때는 아직 참여 중이라 이탈이 통과한다.)
        assertThat(leaveSucceeded.get()).isEqualTo(1);
        // 인증은 순서에 따라 성공하거나 NOT_PARTICIPATING으로 거절된다. 둘 중 하나여야 한다.
        assertThat(verifySucceeded.get() + verifyRejected.get()).isEqualTo(1);

        // 핵심 검증: 인증이 오래된 스냅샷을 flush해 이탈을 되돌리면 안 된다.
        // 락이 없으면 인증의 전체 컬럼 UPDATE가 active=true로 되살릴 수 있었다.
        MemberChallenge reloaded = memberChallengeRepository
                .findByMemberIdAndChallengeId(memberId, challengeId).orElseThrow();
        assertThat(reloaded.isParticipating()).isFalse();
        // 회차도 임의로 바뀌지 않는다(두 명령 모두 회차를 올리지 않는다).
        assertThat(reloaded.getParticipationRound()).isEqualTo(1);
    }
}
