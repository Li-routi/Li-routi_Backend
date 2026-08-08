package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.client.AnthropicVerificationReviewClient;
import com.lirouti.domain.challenge.client.ReviewRejection;
import com.lirouti.domain.challenge.client.VerificationReview;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;

/**
 * 인증 사진 AI 심사.
 *
 * <p>가장 중요한 것은 <b>심사기가 죽었을 때 인증이 막히지 않는가</b>이다(fail-open).
 * 반려 동작만 검증하면 장애 시 서비스가 멈추는 구현도 통과한다.
 *
 * <p>실제 Anthropic 호출은 하지 않는다. 외부 API 응답에 테스트가 매달리면 결정적이지 않고,
 * 여기서 보려는 것은 우리 쪽 분기이지 모델의 판단력이 아니다.
 */
@SpringBootTest
@Transactional
@DisplayName("인증 사진 AI 심사 테스트")
class ChallengeAiReviewTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String STAGING_KEY =
            "challenge-verifications-staging/2026/07/31/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee.jpg";
    private static final String PUBLIC_KEY =
            "challenge-verifications/2026/07/31/11111111-1111-4111-8111-111111111111.jpg";

    @Autowired
    private ChallengeCommandService challengeCommandService;
    @Autowired
    private ChallengeVerificationRepository verificationRepository;

    @MockitoBean
    private AnthropicVerificationReviewClient reviewClient;
    @MockitoBean
    private MediaService mediaService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();
    private Long memberId;
    private Long challengeId;

    @BeforeEach
    void setUp() {
        // 승격은 S3 복사라 목으로 둔다. 이 테스트가 보는 것은 심사 결과이지 승격이 아니다.
        // promote 는 대기 key 를 받아 UUID 가 새로 뽑힌 공개 key 를 돌려준다.
        // 받은 값을 그대로 돌려주면 승격이 아무 일도 안 해도 테스트가 통과한다.
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("ai" + n + "@ex.com").nickname("ai" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("ai-sid-" + n).build();
        em.persist(m);
        Challenge c = Challenge.builder()
                .name("물 1L 마시기").description("하루에 물 1L 이상")
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        em.persist(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());
        em.flush();

        memberId = m.getId();
        challengeId = c.getId();

        // 심사 앞단(형식·바이트 검증)은 이 테스트의 관심사가 아니라 통과시킨다.
        when(mediaService.loadForReview(eq(STAGING_KEY), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(MediaImageLoad.loaded(new MediaImage(new byte[] {1, 2, 3}, "image/jpeg", "etag-test")));
    }

    private ChallengeVerificationReqDTO.Verify request() {
        return new ChallengeVerificationReqDTO.Verify(STAGING_KEY, "오늘도 마셨어요");
    }

    private long savedCount() {
        em.flush();
        em.clear();
        return verificationRepository.count();
    }

    @Test
    @DisplayName("심사를 통과하면 인증이 저장된다")
    void verify_Approved_IsSaved() {
        // given
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());
        long before = savedCount();

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        assertThat(savedCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("통과·반려·장애 세 경우 모두 심사 흔적이 남는다 — 로그가 비면 이상 신호여야 한다")
    void verify_AllOutcomes_AreObservable() {
        // 통과에도 로그가 없으면 "요청이 없었다"와 "전부 통과했다"가 구분되지 않는다.
        // 로그 문자열 자체를 단언하는 대신, 세 경로가 서로 다른 결과로 갈리는지를 본다.
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());
        long before = savedCount();
        challengeCommandService.verify(memberId, challengeId, request());
        assertThat(savedCount()).as("통과는 저장된다").isEqualTo(before + 1);

        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.transientFailure("장애"));
        challengeCommandService.verify(memberId, challengeId, request());
        assertThat(savedCount()).as("장애도 통과시킨다(덮어쓰기)").isEqualTo(before + 1);

        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.reject(ReviewRejection.MISMATCH, "무관한 사진"));
        assertThatThrownBy(() -> challengeCommandService.verify(memberId, challengeId, request()))
                .as("반려는 막힌다")
                .isInstanceOf(VerificationException.class);
    }

    @Test
    @DisplayName("심사에서 반려되면 422로 막고 저장하지 않는다")
    void verify_Rejected_IsBlocked() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.reject(ReviewRejection.MISMATCH, "물이 아니라 커피로 보입니다."));
        long before = savedCount();

        // when & then
        assertThatThrownBy(() -> challengeCommandService.verify(memberId, challengeId, request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.VERIFICATION_REJECTED_BY_REVIEW);
        assertThat(savedCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("심사기가 답을 못 주면 통과시킨다 — 외부 API 장애로 인증이 막히면 안 된다")
    void verify_Undecided_PassesThrough() {
        // given: 장애·타임아웃·응답 이상은 전부 undecided 로 돌아온다
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.transientFailure("장애"));
        long before = savedCount();

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        assertThat(savedCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("사진을 읽지 못해도 통과시킨다 — S3 장애가 인증을 막으면 안 된다")
    void verify_ImageUnavailable_PassesThrough() {
        // given
        when(mediaService.loadForReview(eq(STAGING_KEY), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(MediaImageLoad.readFailed());
        long before = savedCount();

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        assertThat(savedCount()).isEqualTo(before + 1);
        verify(reviewClient, never()).review(any(), any(), any());
    }

    @Test
    @DisplayName("유해로 반려되면 다른 코드로 막는다 — 다시 찍으면 되는 것과 올리면 안 되는 것은 다르다")
    void verify_RejectedAsUnsafe_UsesDistinctCode() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.reject(ReviewRejection.UNSAFE, "노출이 과합니다."));

        // when & then
        assertThatThrownBy(() -> challengeCommandService.verify(memberId, challengeId, request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.VERIFICATION_REJECTED_AS_UNSAFE);
    }

    @Test
    @DisplayName("반려되면 대기본을 지운다 — 승격 전이라 지울 대상은 대기본이다")
    void verify_Rejected_DeletesStagingObject() {
        // given
        when(reviewClient.review(any(), any(), any()))
                .thenReturn(VerificationReview.reject(ReviewRejection.UNSAFE, "노출이 과합니다."));

        // when
        assertThatThrownBy(() -> challengeCommandService.verify(memberId, challengeId, request()))
                .isInstanceOf(VerificationException.class);

        // then
        verify(mediaService).deleteQuietly(STAGING_KEY);
    }

    @Test
    @DisplayName("통과하면 공개본은 남기고 대기본만 지운다")
    void verify_Approved_KeepsPublicAndDropsStaging() {
        // given
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then — 저장이 커밋된 뒤 대기본을 지운다. 공개본을 지우면 방금 저장한 인증의 사진이 사라진다.
        verify(mediaService).deleteQuietly(STAGING_KEY);
        verify(mediaService, never()).deleteQuietly(PUBLIC_KEY);
    }

    @Test
    @DisplayName("심사기가 답을 못 줘 통과한 경우에도 공개본은 남는다 — 장애는 반려가 아니다")
    void verify_Undecided_KeepsObject() {
        // given
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.transientFailure("장애"));

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then — 통과 경로와 같다. 대기본은 지우고 공개본은 남긴다.
        verify(mediaService).deleteQuietly(STAGING_KEY);
        verify(mediaService, never()).deleteQuietly(PUBLIC_KEY);
    }

    @Test
    @DisplayName("심사가 꺼져 있는 것과 장애는 다른 결과다 — 킬 스위치가 보류로 빠지면 안 된다")
    void reviewOutcome_DisabledAndFailure_AreDistinguished() {
        // given & then — 킬 스위치는 보류 대상이 아니다. 보류로 다루면 스위치를 켠 순간
        // 아무도 인증을 못 하게 되어, 스위치를 만든 목적과 정반대가 된다.
        assertThat(VerificationReview.disabled().shouldHold()).isFalse();
        assertThat(VerificationReview.notApplicable("사진 상한 초과").shouldHold()).isFalse();
        assertThat(VerificationReview.transientFailure("타임아웃").shouldHold()).isTrue();

        // 통과·반려는 판정이 난 것이라 보류가 아니다.
        assertThat(VerificationReview.pass().shouldHold()).isFalse();
        assertThat(VerificationReview.reject(ReviewRejection.UNSAFE, "x").shouldHold()).isFalse();
    }

    @Test
    @DisplayName("사진을 못 읽은 이유가 결과에 그대로 옮겨진다 — S3 오류는 보류, 상한 초과는 아니다")
    void loadFailure_MapsToOutcome() {
        // given: S3 를 못 읽었다
        when(mediaService.loadForReview(eq(STAGING_KEY), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(MediaImageLoad.readFailed());

        // when & then — 지금은 둘 다 통과시킨다. 갈리는 것은 결과 값이지 동작이 아니다.
        long before = savedCount();
        challengeCommandService.verify(memberId, challengeId, request());
        assertThat(savedCount()).as("보류 처리는 다음 단계다. 지금은 통과시킨다").isEqualTo(before + 1);

        assertThat(MediaImageLoad.readFailed().failure())
                .isEqualTo(MediaImageLoad.Failure.READ_FAILED);
        assertThat(MediaImageLoad.tooLarge().failure())
                .isEqualTo(MediaImageLoad.Failure.TOO_LARGE);
    }

    @Test
    @DisplayName("참여 중이 아니면 심사를 부르지 않고 409로 막는다 — 유료 호출을 아끼고 응답 코드도 맞춘다")
    void verify_NotParticipating_SkipsReview() {
        // given: 이 챌린지에 참여하지 않은 회원
        Member outsider = Member.builder()
                .email("outsider@ex.com").nickname("outsider")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("outsider-sid").build();
        em.persist(outsider);
        em.flush();

        // when & then
        assertThatThrownBy(() ->
                challengeCommandService.verify(outsider.getId(), challengeId, request()))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.NOT_PARTICIPATING);
        verify(reviewClient, never()).review(any(), any(), any());
    }

    @Test
    @DisplayName("판정 기준으로 챌린지 이름과 설명을 넘긴다 — 카테고리로는 물과 우유를 못 가른다")
    void verify_PassesChallengeIntent() {
        // given
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());

        // when
        challengeCommandService.verify(memberId, challengeId, request());

        // then
        verify(reviewClient).review(eq("물 1L 마시기"), eq("하루에 물 1L 이상"), any());
    }
}
