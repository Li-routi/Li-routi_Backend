package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.client.AnthropicVerificationReviewClient;
import com.lirouti.domain.challenge.client.ReviewRejection;
import com.lirouti.domain.challenge.client.VerificationReview;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 보류가 <b>결국 풀리는지</b>를 본다.
 *
 * <p>재심사가 없으면 보류는 쌓이기만 하고 사용자 사진은 영영 "심사 중"으로 남는다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다 — 재심사는 트랜잭션 밖에서 돌고 안쪽에서 여러 번
 * 커밋하므로, 테스트가 트랜잭션을 잡고 있으면 그 경계가 사라져 실제와 다른 것을 보게 된다.
 * 대신 각 테스트가 자기 데이터를 직접 치운다.
 */
@SpringBootTest
@TestPropertySource(properties = "ai.review.pending.enabled=true")
@DisplayName("보류 인증 재심사")
class PendingResweepTest {

    private static final String STAGING_KEY =
            "challenge-verifications-staging/2026/08/08/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";
    private static final String PUBLIC_KEY =
            "challenge-verifications/2026/08/08/dddddddd-dddd-4ddd-8ddd-dddddddddddd.jpg";

    @Autowired
    private PendingReviewService pendingReviewService;

    @Autowired
    private ChallengeVerificationRepository challengeVerificationRepository;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private AnthropicVerificationReviewClient reviewClient;

    @PersistenceContext
    private EntityManager em;

    /**
     * 픽스처를 만들고 검증을 읽을 때 쓰는 트랜잭션.
     *
     * <p>이 테스트에 {@code @Transactional} 을 붙일 수 없어 직접 연다 — 재심사가 안쪽에서
     * 여러 번 커밋하므로, 테스트가 트랜잭션을 잡고 있으면 그 경계가 사라진다.
     */
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Long verificationId;
    private Long memberChallengeId;

    @BeforeEach
    void setUp() {
        when(mediaService.loadForReview(any(), anyInt()))
                .thenReturn(MediaImageLoad.loaded(new MediaImage(new byte[] {1}, "image/jpeg", "etag")));
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);
    }

    /**
     * 만든 데이터를 치운다. 이 테스트는 트랜잭션 롤백에 기대지 못하므로 직접 지운다 —
     * 남겨 두면 다음 실행의 유니크 제약(social_id)에 걸려 <b>엉뚱한 테스트가 깨진다.</b>
     * 실제로 그렇게 깨진 적이 있다.
     */
    @AfterEach
    void tearDown() {
        tx().executeWithoutResult(status -> {
            em.createQuery("delete from ChallengeVerification v where v.memberChallenge.id = :id")
                    .setParameter("id", memberChallengeId).executeUpdate();
            em.createQuery("delete from MemberChallenge mc where mc.id = :id")
                    .setParameter("id", memberChallengeId).executeUpdate();
            em.createQuery("delete from Member m where m.socialId like 'resweep-sid-%'").executeUpdate();
            em.createQuery("delete from Challenge c where c.name = '물 마시기' and c.id not in "
                    + "(select mc2.challenge.id from MemberChallenge mc2)").executeUpdate();
        });
    }

    /** 보류 상태의 인증 한 건을 만든다. {@code pendingSince} 를 조절해 상한 도달을 흉내낸다. */
    private void givenPending(LocalDateTime pendingSince, int attempts) {
        tx().executeWithoutResult(status -> persistPending(pendingSince, attempts));
    }

    private void persistPending(LocalDateTime pendingSince, int attempts) {
        int n = SEQ.incrementAndGet();
        Member m = Member.builder()
                .email("resweep" + n + "@ex.com").nickname("rs" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("resweep-sid-" + n).build();
        em.persist(m);

        Challenge c = Challenge.builder()
                .name("물 마시기").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);

        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(1)
                .lastVerifiedDate(LocalDate.now())
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);

        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(LocalDate.now()).verifiedAt(LocalDateTime.now())
                .imageUrl(STAGING_KEY).content("보류 건")
                .reviewStatus(ReviewStatus.PENDING).pendingSince(pendingSince)
                .build();
        em.persist(v);
        em.flush();

        // 시도 횟수는 빌더가 0 으로 고정하므로 직접 올린다.
        for (int i = 0; i < attempts; i++) {
            v.recordReviewAttempt();
        }
        em.flush();
        em.clear();

        verificationId = v.getId();
        memberChallengeId = mc.getId();
    }

    @Test
    @DisplayName("재심사가 통과하면 승격하고 공개로 확정한다")
    void resweep_ReviewPasses_PromotesAndApproves() {
        // given
        givenPending(LocalDateTime.now().minusMinutes(30), 0);
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());

        // when
        pendingReviewService.sweepPending();

        // then
        ChallengeVerification row = challengeVerificationRepository.findById(verificationId).orElseThrow();
        assertThat(row.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(row.getImageUrl()).isEqualTo(PUBLIC_KEY);
        assertThat(row.getPendingSince()).isNull();
    }

    @Test
    @DisplayName("아직 답을 못 주면 보류를 유지하고 시도만 올린다")
    void resweep_StillFailing_KeepsPendingAndCountsAttempt() {
        // given
        givenPending(LocalDateTime.now().minusMinutes(30), 0);
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.transientFailure("여전히 장애"));

        // when
        pendingReviewService.sweepPending();

        // then
        ChallengeVerification row = challengeVerificationRepository.findById(verificationId).orElseThrow();
        assertThat(row.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(row.getReviewAttempts())
                .as("실패해도 시도는 오른다 — 안 그러면 계속 죽는 호출이 상한에 안 닿는다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("상한을 넘기면 심사 없이 통과시킨다 — 남의 장애로 반려하지 않는다")
    void resweep_OverMaxAge_ApprovesWithoutReview() {
        // given — 보류 상한(24시간)을 넘겼다
        givenPending(LocalDateTime.now().minusHours(25), 0);

        // when
        pendingReviewService.sweepPending();

        // then
        ChallengeVerification row = challengeVerificationRepository.findById(verificationId).orElseThrow();
        assertThat(row.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(row.getImageUrl()).isEqualTo(PUBLIC_KEY);
    }

    @Test
    @DisplayName("시도 상한에 닿아도 통과시킨다 — 시간과 횟수 중 먼저 닿는 쪽이다")
    void resweep_OverMaxAttempts_ApprovesWithoutReview() {
        // given — 시간은 남았지만 12회를 채웠다
        givenPending(LocalDateTime.now().minusMinutes(10), 12);

        // when
        pendingReviewService.sweepPending();

        // then
        assertThat(challengeVerificationRepository.findById(verificationId).orElseThrow()
                .getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    /** 당일 재인증이 들어와 사진이 갈린 상황. 별도 트랜잭션으로 즉시 커밋한다. */
    private void replacePhoto(String newKey) {
        tx().executeWithoutResult(status -> {
            ChallengeVerification v = em.find(ChallengeVerification.class, verificationId);
            v.reverify(newKey, "새 사진", LocalDateTime.now(),
                    ReviewStatus.PENDING, LocalDateTime.now());
        });
    }

    @Test
    @DisplayName("그 사이 재인증이 들어왔으면 옛 심사 결과를 버린다 — 새 사진을 옛 판정으로 공개하면 안 된다")
    void resweep_PhotoReplacedMidReview_DiscardsResult() {
        // given — 보류 건을 잡아 두고, 심사하는 사이에 당일 재인증이 들어와 사진이 바뀐 상황
        givenPending(LocalDateTime.now().minusMinutes(30), 0);

        String replacedKey = "challenge-verifications-staging/2026/08/08/"
                + "99999999-9999-4999-8999-999999999999.jpg";
        // 심사가 도는 "동안" 사진이 바뀌어야 경합이다. 심사 응답 직전에 갈아끼운다 —
        // 미리 바꿔 두면 재심사가 처음부터 새 사진을 보게 되어 경합이 아니다.
        when(reviewClient.review(any(), any(), any())).thenAnswer(invocation -> {
            replacePhoto(replacedKey);
            return VerificationReview.pass();
        });

        // when
        pendingReviewService.sweepPending();

        // then — 옛 사진의 통과 결과로 새 사진을 공개하면 안 된다.
        ChallengeVerification row = challengeVerificationRepository.findById(verificationId).orElseThrow();
        assertThat(row.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(row.getImageUrl()).isEqualTo(replacedKey);
    }

    @Test
    @DisplayName("그 사이 사진이 바뀌었으면 반려도 버린다 — 방금 올린 멀쩡한 사진이 사라지면 안 된다")
    void resweep_PhotoReplacedMidReview_DiscardsRejection() {
        // given
        givenPending(LocalDateTime.now().minusMinutes(30), 0);

        String replacedKey = "challenge-verifications-staging/2026/08/08/"
                + "88888888-8888-4888-8888-888888888888.jpg";
        when(reviewClient.review(any(), any(), any())).thenAnswer(invocation -> {
            replacePhoto(replacedKey);
            return VerificationReview.reject(ReviewRejection.MISMATCH, "관계 없는 사진");
        });

        // when
        pendingReviewService.sweepPending();

        // then
        assertThat(challengeVerificationRepository.findById(verificationId))
                .as("옛 사진의 반려로 새 사진을 지우면 안 된다")
                .isPresent();
    }

    @Test
    @DisplayName("원본이 사라졌으면 정리한다 — 안 그러면 10분마다 영원히 실패한다")
    void resweep_SourceGone_CleansUpInsteadOfLoopingForever() {
        // given — 대기본이 수명 주기에 지워졌거나 앞 단계가 중간에 죽은 상태
        givenPending(LocalDateTime.now().minusHours(25), 0);
        when(mediaService.promote(any(), any(), any()))
                .thenThrow(new MediaException(MediaErrorCode.MEDIA_SOURCE_GONE));

        // when
        pendingReviewService.sweepPending();

        // then — 되살릴 수 없으므로 남겨 두지 않는다. 남기면 "심사 중" 이 영원히 표시된다.
        assertThat(challengeVerificationRepository.findById(verificationId)).isEmpty();
    }

    @Test
    @DisplayName("일시적 승격 실패는 보류를 유지한다 — 다시 하면 될 수 있다")
    void resweep_PromotionFails_KeepsPending() {
        // given
        givenPending(LocalDateTime.now().minusHours(25), 0);
        when(mediaService.promote(any(), any(), any()))
                .thenThrow(new MediaException(MediaErrorCode.MEDIA_PROMOTION_FAILED));

        // when
        pendingReviewService.sweepPending();

        // then
        assertThat(challengeVerificationRepository.findById(verificationId).orElseThrow()
                .getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    @DisplayName("재심사가 반려하면 행을 지우고 스트릭을 다시 센다")
    void resweep_ReviewRejects_DeletesRowAndRecalculatesStreak() {
        // given
        givenPending(LocalDateTime.now().minusMinutes(30), 0);
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.reject(ReviewRejection.MISMATCH, "관계 없는 사진"));

        // when
        pendingReviewService.sweepPending();

        // then — 반려 행을 남기면 유니크 키가 그날의 재시도를 막는다.
        assertThat(challengeVerificationRepository.findById(verificationId)).isEmpty();

        MemberChallenge mc = tx().execute(status ->
                em.find(MemberChallenge.class, memberChallengeId));
        assertThat(mc.getCurrentStreak())
                .as("유효한 인증이 하나도 없으므로 0 으로 되돌아간다")
                .isZero();
        assertThat(mc.getLastVerifiedDate()).isNull();
    }
}
