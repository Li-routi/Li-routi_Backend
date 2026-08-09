package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationLikeRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 "따닥" 동시성 테스트(#63). @Transactional을 쓰지 않는다 — 두 스레드가 각자 트랜잭션으로
 * 커밋된 같은 인증을 두고 경합해야 하므로 셋업 데이터를 실제로 커밋하고 뒤에서 정리한다.
 *
 * 지키려는 불변식은 <b>"두 번 눌러도 행이 하나, 아무도 오류를 받지 않는다"</b>이다.
 * 좋아요는 신고와 달리 멱등이라, 진 쪽도 예외가 아니라 최종 상태를 받아야 한다.
 *
 * 구현이 ON DUPLICATE KEY UPDATE라 애초에 예외가 나지 않는다. JPA save + 제약 위반 잡기로
 * 했다면 진 쪽 트랜잭션이 롤백 전용이 되어 이어지는 집계가 커밋에서 터졌을 것이다.
 */
@SpringBootTest
@DisplayName("인증 좋아요 동시성 테스트")
class ChallengeLikeConcurrencyTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY =
            "challenge-verifications/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";

    @Autowired
    private ChallengeVerificationService challengeVerificationService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private MemberChallengeRepository memberChallengeRepository;
    @Autowired
    private ChallengeVerificationRepository challengeVerificationRepository;
    @Autowired
    private ChallengeVerificationLikeRepository likeRepository;

    private Long memberId;
    private Long challengeId;
    private Long memberChallengeId;
    private Long verificationId;

    @BeforeEach
    void setUp() {
        Member m = memberRepository.save(Member.builder()
                .email("lconc@ex.com").nickname("lconc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("lconc-sid").build());
        Challenge c = challengeRepository.save(Challenge.builder()
                .name("lconc챌린지").category(ChallengeCategory.HEALTH).active(true).build());
        MemberChallenge mc = memberChallengeRepository.save(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build());
        ChallengeVerification v = challengeVerificationRepository.save(ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(LocalDate.now(KST)).verifiedAt(LocalDateTime.now())
                .imageUrl(KEY).content("동시 좋아요 대상").build());

        memberId = m.getId();
        challengeId = c.getId();
        memberChallengeId = mc.getId();
        verificationId = v.getId();
    }

    @AfterEach
    void tearDown() {
        // 리포지토리의 벌크 삭제는 @Modifying이라 트랜잭션이 필요하다. 이 테스트는 @Transactional을
        // 쓰지 않으므로(스레드마다 각자 커밋해야 한다) 트랜잭션을 가진 서비스 쪽을 부른다.
        challengeVerificationService.unlike(memberId, challengeId, verificationId);
        challengeVerificationRepository.deleteById(verificationId);
        memberChallengeRepository.deleteById(memberChallengeId);
        challengeRepository.deleteById(challengeId);
        memberRepository.deleteById(memberId);
    }

    /** 두 스레드를 같은 게이트에 세웠다 동시에 출발시킨다(직렬 실행으로 새는 것 방지). */
    private Result runConcurrently(Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    task.run();
                    success.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).as("두 스레드가 게이트에 도착").isTrue();
        assertThat(finished).as("20초 안에 완료").isTrue();
        return new Result(success.get(), failure.get());
    }

    private record Result(int success, Throwable failure) {
    }

    @Test
    @DisplayName("같은 좋아요가 동시에 들어와도 둘 다 성공하고 행은 하나다")
    void concurrentLike_BothSucceed_SingleRow() throws InterruptedException {
        Result result = runConcurrently(() ->
                challengeVerificationService.like(memberId, challengeId, verificationId));

        assertThat(result.failure()).as("멱등이므로 아무도 오류를 받지 않는다").isNull();
        assertThat(result.success()).isEqualTo(2);
        assertThat(likeRepository.countByVerificationIds(java.util.List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 취소가 동시에 들어와도 둘 다 성공하고 행이 남지 않는다")
    void concurrentUnlike_BothSucceed_NoRow() throws InterruptedException {
        challengeVerificationService.like(memberId, challengeId, verificationId);

        Result result = runConcurrently(() ->
                challengeVerificationService.unlike(memberId, challengeId, verificationId));

        assertThat(result.failure()).isNull();
        assertThat(result.success()).isEqualTo(2);
        assertThat(likeRepository.countByVerificationIds(java.util.List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isZero();
    }
}
