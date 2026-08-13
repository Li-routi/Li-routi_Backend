package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.global.apiPayload.exception.GeneralException;
import com.lirouti.global.util.TimeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.lirouti.support.testdb.MemberFixtureCleanup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

/**
 * <b>주간</b> 챌린지의 "따닥" 동시성. 기존 동시성 테스트는 {@code DAILY} 라 <b>옛 키로도
 * 통과한다</b> — 하루에 한 번이면 {@code verified_date} 와 {@code period_start_date} 가 같은
 * 값이기 때문이다.
 *
 * <p>운영에 주간 15개·월간 13개가 이미 나가 있어(챌린지 66개 확장) 이 경로는 더 이상 가정이
 * 아니다. 선검사는 잠금 없이 도는 조회라 동시 요청 둘이 함께 통과할 수 있고, 그때 막는 것은
 * 유니크 제약뿐이다.
 *
 * <p>{@code @Transactional} 을 쓰지 않는다 — 두 스레드가 각자 트랜잭션으로 커밋된 같은 참여를
 * 두고 경합해야 하므로 셋업을 실제로 커밋하고 뒤에서 정리한다.
 */
@SpringBootTest
@DisplayName("주간 챌린지 인증 동시성 테스트")
class WeeklyVerificationConcurrencyTest {

    private static final String KEY_A =
            "challenge-verifications-staging/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";
    private static final String KEY_B =
            "challenge-verifications-staging/dddddddd-dddd-4ddd-8ddd-dddddddddddd.jpg";
    private static final String PUBLIC_KEY =
            "challenge-verifications/22222222-2222-4222-8222-222222222222.jpg";

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
        when(mediaService.loadForReview(any(), anyInt())).thenReturn(MediaImageLoad.readFailed());
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);
        when(mediaService.resolvePublicUrl(any())).thenReturn("https://cdn.example.com/x.jpg");

        Member m = memberRepository.save(Member.builder()
                .email("wconc@ex.com").nickname("wconc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("wconc-sid").build());
        Challenge c = challengeRepository.save(Challenge.builder()
                .name("wconc주간챌린지").category(ChallengeCategory.HEALTH)
                .routineCycle(RoutineCycle.WEEKLY).active(true).build());
        MemberChallenge mc = memberChallengeRepository.save(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());

        memberId = m.getId();
        challengeId = c.getId();
        memberChallengeId = mc.getId();
    }

    /**
     * 이 참여의 인증을 <b>상태와 무관하게</b> 모은다.
     *
     * <p>{@code findFeedByCursor} 는 피드용이라 숨김 처리된 행을 걸러 낸다. 정리와 단정에 그것을
     * 쓰면 걸러진 행을 못 보고 지나가, 남은 행이 다음 테스트의 유니크 키를 막는다.
     */
    private List<ChallengeVerification> allRows() {
        return challengeVerificationRepository.findAll().stream()
                .filter(v -> memberChallengeId.equals(v.getMemberChallenge().getId()))
                .toList();
    }

    @AfterEach
    void tearDown() {
        challengeVerificationRepository.deleteAll(allRows());
        memberChallengeRepository.deleteById(memberChallengeId);
        challengeRepository.deleteById(challengeId);
        // 회원을 참조하는 표(업적·스트릭·활동일)를 먼저 지운다. 안 지우면 외래 키가 회원
        // 삭제를 막고, 다음 테스트가 같은 이메일로 회원을 만들다 중복 키로 죽는다.
        MemberFixtureCleanup.deleteDependencies(jdbcTemplate, memberId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("주간 인증이 동시에 들어와도 그 주의 인증은 한 건뿐이다")
    void concurrentWeeklyVerify_KeepsSingleRow() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        for (String mediaKey : new String[]{KEY_A, KEY_B}) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    challengeVerificationService.verify(memberId, challengeId,
                            new ChallengeVerificationReqDTO.Verify(mediaKey, "주간 동시 인증"));
                    success.incrementAndGet();
                } catch (GeneralException e) {
                    // 진 쪽이 받는 코드가 셋이다. 참여 행 잠금 뒤에 이미 커밋된 인증을 보면
                    // ALREADY_VERIFIED_IN_PERIOD, 그보다 앞서 INSERT 까지 갔다가 유니크
                    // 제약에 걸리면 VERIFICATION_CONFLICT 다. 어느 쪽이든 "졌다" 는 같다.
                    if (e.getCode() == ChallengeVerificationErrorCode.VERIFICATION_CONFLICT
                            || e.getCode() == ChallengeVerificationErrorCode.ALREADY_VERIFIED_IN_PERIOD
                            || e.getCode() == ChallengeVerificationErrorCode.ALREADY_VERIFIED_TODAY) {
                        conflict.incrementAndGet();
                    } else {
                        unexpected.compareAndSet(null, e);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    unexpected.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(unexpected.get()).as("예상 밖 예외가 없어야 한다").isNull();
        assertThat(success.get()).as("한 건만 통과한다").isEqualTo(1);
        assertThat(conflict.get()).as("나머지 하나는 진다").isEqualTo(1);

        assertThat(allRows())
                .as("그 주에 남는 인증은 한 건이다")
                .hasSize(1);
    }

    @Test
    @DisplayName("저장된 구간 첫날은 그 주 일요일이다 — 동시 경합을 이긴 쪽도 마찬가지")
    void concurrentWeeklyVerify_WinnerStoresSundayPeriodStart() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        for (String mediaKey : new String[]{KEY_A, KEY_B}) {
            pool.submit(() -> {
                try {
                    // 둘 다 게이트 앞에 선 뒤 함께 출발시킨다. 이것이 없으면 한쪽이 먼저
                    // 끝나 버려 경합이 아니라 순차 실행을 보게 된다.
                    ready.countDown();
                    start.await();
                    challengeVerificationService.verify(memberId, challengeId,
                            new ChallengeVerificationReqDTO.Verify(mediaKey, "주간 동시 인증"));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                    // 진 쪽의 예외는 위 테스트가 본다.
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        LocalDate expected = RoutineCycle.WEEKLY.currentPeriodStart(LocalDate.now(TimeUtil.KST));
        assertThat(allRows())
                .singleElement()
                .satisfies(v -> assertThat(v.getPeriodStartDate()).isEqualTo(expected));
    }
}
