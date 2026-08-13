package com.lirouti.domain.verification.service;

import com.lirouti.global.apiPayload.exception.GeneralException;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
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
import com.lirouti.support.testdb.MemberFixtureCleanup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;
import com.lirouti.domain.media.service.MediaService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

    private static final String KEY_A = "challenge-verifications-staging/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    private static final String KEY_B = "challenge-verifications-staging/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";
    private static final String PUBLIC_KEY = "challenge-verifications/11111111-1111-4111-8111-111111111111.jpg";

    // 미디어는 이 테스트의 관심사가 아니다. 승격이 S3 를 호출하므로 목으로 끊는다.
    @MockitoBean
    private MediaService mediaService;

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
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long challengeId;
    private Long memberChallengeId;

    @BeforeEach
    void setUp() {
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());
        // 심사용 사진을 읽는 단계. 이 테스트들의 관심사가 아니라 "못 읽음"으로 둔다 —
        // 그러면 심사를 건너뛰고 통과한다. 스텁이 없으면 record 기본값이 null 이라 NPE 다.
        when(mediaService.loadForReview(any(), anyInt())).thenReturn(MediaImageLoad.readFailed());
        // promote 는 대기 key 를 받아 UUID 가 새로 뽑힌 공개 key 를 돌려준다.
        // 받은 값을 그대로 돌려주면 승격이 아무 일도 안 해도 테스트가 통과한다.
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);
        when(mediaService.resolvePublicUrl(any())).thenReturn("https://cdn.example.com/x.jpg");
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
                challengeVerificationRepository.findFeedByCursor(challengeId, null, null, 100));
        memberChallengeRepository.deleteById(memberChallengeId);
        challengeRepository.deleteById(challengeId);
        // 회원을 참조하는 표(업적·스트릭·활동일)를 먼저 지운다. 안 지우면 외래 키가 회원
        // 삭제를 막고, 다음 테스트가 같은 이메일로 회원을 만들다 중복 키로 죽는다.
        MemberFixtureCleanup.deleteDependencies(jdbcTemplate, memberId);
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
                    challengeVerificationService.verify(
                            memberId, challengeId, new ChallengeVerificationReqDTO.Verify(mediaKey, "동시 인증"));
                    success.incrementAndGet();
                } catch (GeneralException e) {
                    // 진 쪽이 받는 코드가 둘이다. 참여 행 잠금 뒤에 읽어 이미 커밋된 인증을
                    // 보면 ALREADY_VERIFIED_TODAY, 그보다 앞서 INSERT 까지 갔다가 유니크
                    // 제약에 걸리면 VERIFICATION_CONFLICT 다. 어느 쪽이든 "졌다" 는 같다.
                    if (e.getCode() == ChallengeVerificationErrorCode.VERIFICATION_CONFLICT
                            || e.getCode() == ChallengeVerificationErrorCode.ALREADY_VERIFIED_TODAY) {
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
        assertThat(challengeVerificationRepository.findFeedByCursor(challengeId, null, null, 100)).hasSize(1);

        // 두 요청이 완전히 겹치면 진 쪽은 409고, 한쪽이 늦게 읽으면 덮어쓰기로 성공한다.
        // 스케줄링에 달렸으므로 성공 건수를 1로 못 박지 않고, 둘 중 하나였는지만 확인한다.
        assertThat(success.get()).isPositive();
        assertThat(success.get() + conflict.get()).isEqualTo(2);
    }
}
