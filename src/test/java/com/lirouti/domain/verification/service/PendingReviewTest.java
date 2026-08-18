package com.lirouti.domain.verification.service;

import com.lirouti.domain.verification.enums.ReportType;
import com.lirouti.domain.verification.client.OpenAiVerificationReviewClient;
import com.lirouti.domain.verification.client.ReviewRejection;
import com.lirouti.domain.verification.client.VerificationReview;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * 심사가 답을 못 줬을 때 <b>보류로 저장되는지</b>를 본다.
 *
 * <p>테스트 기본 설정은 보류를 꺼 두었다 — mock S3 때문에 심사용 사진 읽기가 늘 실패해서,
 * 켜 두면 인증을 다루는 모든 테스트가 보류가 된다. 여기서만 켠다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "ai.review.pending.enabled=true")
@DisplayName("심사 보류 저장")
class PendingReviewTest {

    private static final String STAGING_KEY =
            "challenge-verifications-staging/2026/08/08/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    private static final String PUBLIC_KEY =
            "challenge-verifications/2026/08/08/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";

    @Autowired
    private ChallengeVerificationService challengeVerificationService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private OpenAiVerificationReviewClient reviewClient;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Long memberId;
    private Long challengeId;
    private MemberChallenge participation;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("pend" + n + "@ex.com").nickname("pend" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("pend-sid-" + n).build();
        em.persist(m);

        Challenge c = Challenge.builder()
                .name("물 마시기").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);

        participation = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(participation);
        em.flush();

        memberId = m.getId();
        challengeId = c.getId();

        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());
        when(mediaService.loadForReview(any(), anyInt()))
                .thenReturn(MediaImageLoad.loaded(new MediaImage(new byte[] {1}, "image/jpeg", "etag")));
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);
        when(mediaService.resolvePublicUrl(any())).thenReturn("https://cdn.example.com/" + PUBLIC_KEY);
        when(mediaService.presignedViewUrl(any())).thenReturn("https://signed.example.com/staging?sig=x");
    }

    private ChallengeVerificationReqDTO.Verify request() {
        return new ChallengeVerificationReqDTO.Verify(STAGING_KEY, "오늘도 마셨어요");
    }

    private ChallengeVerification saved() {
        List<ChallengeVerification> rows = em.createQuery(
                        "select v from ChallengeVerification v where v.memberChallenge.id = :id",
                        ChallengeVerification.class)
                .setParameter("id", participation.getId())
                .getResultList();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("심사가 장애로 답을 못 주면 보류로 저장하고 승격하지 않는다")
    void verify_TransientFailure_SavesPending() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.transientFailure("타임아웃"));

        // when
        ChallengeVerificationResDTO.Verification result =
                challengeVerificationService.verify(memberId, challengeId, request());

        // then
        ChallengeVerification row = saved();
        assertThat(row.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(row.getPendingSince()).isNotNull();
        assertThat(row.getReviewAttempts()).isZero();

        // 승격하지 않았으므로 저장된 것은 대기 key 그대로다.
        assertThat(row.getImageUrl()).isEqualTo(STAGING_KEY);

        // 공개 주소가 없으므로 서명 주소가 나간다. 공개 주소로 열면 403 이다.
        assertThat(result.imageUrl()).startsWith("https://signed.example.com/");
    }

    @Test
    @DisplayName("보류여도 스트릭은 오른다 — 남의 장애로 내 기록이 끊기면 안 된다")
    void verify_TransientFailure_StillAdvancesStreak() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.transientFailure("타임아웃"));

        // when
        ChallengeVerificationResDTO.Verification result =
                challengeVerificationService.verify(memberId, challengeId, request());

        // then
        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(participation.getLastVerifiedDate()).isNotNull();
    }

    @Test
    @DisplayName("킬 스위치는 보류가 아니라 통과다 — 스위치가 인증을 막으면 안 된다")
    void verify_Disabled_IsNotHeld() {
        // given
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.disabled());

        // when
        challengeVerificationService.verify(memberId, challengeId, request());

        // then
        ChallengeVerification row = saved();
        assertThat(row.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(row.getImageUrl()).isEqualTo(PUBLIC_KEY);
    }

    @Test
    @DisplayName("사진이 심사 상한을 넘은 것도 보류가 아니다 — 다시 해도 같다")
    void verify_TooLarge_IsNotHeld() {
        // given
        when(mediaService.loadForReview(any(), anyInt())).thenReturn(MediaImageLoad.tooLarge());

        // when
        challengeVerificationService.verify(memberId, challengeId, request());

        // then
        assertThat(saved().getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    @DisplayName("반려는 보류하지 않고 그대로 422 다")
    void verify_Rejected_StillFails() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.reject(ReviewRejection.MISMATCH, "관계 없는 사진"));

        // when & then
        assertThatThrownBy(() -> challengeVerificationService.verify(memberId, challengeId, request()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("보류 건에는 좋아요를 못 누른다 — 남에게 보이지 않는 글이다")
    void pendingVerification_CannotBeLiked() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.transientFailure("타임아웃"));
        Long verificationId = challengeVerificationService
                .verify(memberId, challengeId, request()).verificationId();

        // when & then — 좋아요 행이 붙으면 반려 확정 때 인증 삭제가 외래 키에 걸려 실패하고,
        // 그 행은 영원히 보류로 남는다.
        assertThatThrownBy(() -> challengeVerificationService.like(memberId, challengeId, verificationId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("보류 건은 신고할 수 없다 — 공개된 적 없는 사진이 숨김 임계값에 걸리면 안 된다")
    void pendingVerification_CannotBeReported() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.transientFailure("타임아웃"));
        Long verificationId = challengeVerificationService
                .verify(memberId, challengeId, request()).verificationId();

        // when & then
        assertThatThrownBy(() -> challengeVerificationService.report(
                memberId, challengeId, verificationId,
                new ChallengeVerificationReqDTO.Report(ReportType.IRRELEVANT, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("보류 건은 공개 피드에 담기지 않는다 — 승격 전이라 열리지 않는 사진이다")
    void pendingVerification_IsNotInPublicFeed() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.transientFailure("타임아웃"));
        challengeVerificationService.verify(memberId, challengeId, request());
        em.flush();
        em.clear();

        // when — 피드 쿼리가 보는 것과 같은 조건으로 센다.
        Long visible = em.createQuery("""
                        select count(v) from ChallengeVerification v
                        where v.memberChallenge.challenge.id = :challengeId
                          and v.reviewStatus = com.lirouti.domain.verification.enums.ReviewStatus.APPROVED
                        """, Long.class)
                .setParameter("challengeId", challengeId)
                .getSingleResult();

        // then
        assertThat(visible).isZero();
    }
}
