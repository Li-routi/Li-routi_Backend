package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
 * 인증 "따닥" 동시성 테스트. @Transactional을 쓰지 않는다 — 두 스레드가 각자 트랜잭션으로
 * 커밋된 같은 참여를 두고 경합해야 하므로, 셋업 데이터를 실제로 커밋하고 뒤에서 정리한다.
 *
 * 지키려는 불변식은 "스트릭이 두 번 오르지 않는다"이다. 인증 INSERT와 스트릭 갱신이 한 트랜잭션에
 * 있고 유니크 제약 위반 예외를 삼키지 않으므로, 진 쪽은 스트릭 갱신까지 함께 롤백된다.
 */
@SpringBootTest
@DisplayName("챌린지 인증 동시성 테스트")
class ChallengeVerificationConcurrencyTest {

    private static final String KEY_A = "challenge-verifications/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    private static final String KEY_B = "challenge-verifications/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";

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
    private Long memberChallengeId;

    @BeforeEach
    void setUp() {
        Member m = memberRepository.save(Member.builder()
                .email("vconc@ex.com").nickname("vconc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("vconc-sid").build());
        Challenge c = challengeRepository.save(Challenge.builder()
                .name("vconc챌린지").category(ChallengeCategory.HEALTH).active(true).build());
        // 아직 한 번도 인증하지 않은 참여 상태로 커밋해 둔다.
        MemberChallenge mc = memberChallengeRepository.save(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());

        memberId = m.getId();
        challengeId = c.getId();
        memberChallengeId = mc.getId();
    }

    @AfterEach
    void tearDown() {
        // 인증이 참여를 참조하므로 인증부터 지운다.
        challengeVerificationRepository.deleteAll(
                challengeVerificationRepository.findFeedByCursor(challengeId, null, 100));
        memberChallengeRepository.deleteById(memberChallengeId);
        challengeRepository.deleteById(challengeId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("같은 날 인증이 동시에 들어와도 인증은 한 건, 스트릭은 1에서 멈춘다")
    void concurrentVerify_KeepsSingleRowAndSingleStreakIncrement() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for (String mediaKey : new String[]{KEY_A, KEY_B}) {
            pool.submit(() -> {
                try {
                    ready.countDown();       // 게이트 앞 도착 알림
                    start.await();
                    challengeCommandService.verify(
                            memberId, challengeId, new ChallengeReqDTO.Verify(mediaKey, "동시 인증"));
                    success.incrementAndGet();
                } catch (ChallengeException e) {
                    if (e.getCode() == ChallengeErrorCode.VERIFICATION_CONFLICT) {
                        conflict.incrementAndGet();
                    } else {
                        unexpected.compareAndSet(null, e);
                        other.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    unexpected.compareAndSet(null, e);
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        // 두 스레드가 모두 게이트 앞에 도착한 뒤에 출발시킨다(직렬 실행으로 새는 것 방지).
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        // 예상 밖 예외 없음 — 무엇이 터졌는지 바로 보이도록 예외를 메시지에 싣는다.
        assertThat(other.get())
                .withFailMessage("예상하지 못한 예외: %s", unexpected.get())
                .isZero();

        // 이 테스트의 핵심. 유니크 위반을 삼키고 진행하면 여기서 2가 된다.
        MemberChallenge reloaded = memberChallengeRepository.findById(memberChallengeId).orElseThrow();
        assertThat(reloaded.getCurrentStreak()).isEqualTo(1);

        // 하루 1행이 유지된다.
        assertThat(challengeVerificationRepository.findFeedByCursor(challengeId, null, 100)).hasSize(1);

        // 두 요청이 완전히 겹치면 진 쪽은 409고, 한쪽이 늦게 읽으면 덮어쓰기로 성공한다.
        // 스케줄링에 달렸으므로 성공 건수를 1로 못 박지 않고, 둘 중 하나였는지만 확인한다.
        assertThat(success.get()).isPositive();
        assertThat(success.get() + conflict.get()).isEqualTo(2);
    }
}
