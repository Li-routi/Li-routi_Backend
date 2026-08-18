package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.verification.client.OpenAiVerificationReviewClient;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.client.VerificationReview;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;
import com.lirouti.global.util.TimeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이번 구간에 이미 인증했으면 <b>심사·승격 전에</b> 끊는가.
 *
 * <p>거절이 늦으면 두 가지를 헛되이 치른다 — <b>유료 AI 심사</b>와 <b>공개 prefix 승격</b>이다.
 * 뒤엣것이 특히 나쁘다. 승격은 사진을 익명 읽기가 열린 자리로 옮기는데, 그러고 나서 거절하면
 * <b>아무 인증도 참조하지 않는 사진이 공개된 채 남는다.</b> 미참조 정리가 결국 가져가지만
 * 그전까지는 주소를 아는 사람이 열 수 있다.
 *
 * <p>하루짜리 챌린지만 있는 동안에는 이 경로가 거의 안 열렸다. <b>주간·월간이 들어오면
 * "이번 주에 이미 냈다" 가 흔한 응답이 되므로</b> 그만큼 자주 일어난다.
 *
 * <p>그래서 이 테스트가 보는 것은 응답 코드가 아니라 <b>무엇이 불리지 않았는가</b>다.
 */
@SpringBootTest
@Transactional
@DisplayName("구간 선검사 — 심사·승격 전에 끊는다")
class VerificationPeriodPreCheckTest {

    private static final String STAGING_KEY =
            "challenge-verifications-staging/44444444-4444-4444-8444-444444444444.jpg";
    private static final String PUBLIC_KEY =
            "challenge-verifications/55555555-5555-4555-8555-555555555555.jpg";

    @Autowired
    private ChallengeVerificationService challengeVerificationService;

    @MockitoBean
    private OpenAiVerificationReviewClient reviewClient;
    @MockitoBean
    private MediaService mediaService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("pc" + n + "@ex.com").nickname("pc" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("pc-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge(RoutineCycle cycle) {
        Challenge c = Challenge.builder()
                .name("주기챌린지").description("구간 판정용")
                .category(ChallengeCategory.HEALTH)
                .routineCycle(cycle).active(true).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge join(Member m, Challenge c) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        return mc;
    }

    /** 저장 경로를 거치지 않고 인증 행을 심는다 — 심사·승격 호출 수를 0 에서 시작하기 위해서다. */
    private ChallengeVerification seed(MemberChallenge mc, LocalDate periodStart, boolean deleted) {
        LocalDate today = LocalDate.now(TimeUtil.KST);
        // 구간 첫날이 오늘이면(일요일·매월 1일) verified_date 와 period_start_date 가 같아져
        // 두 컬럼을 구분하지 못한다. 같은 구간 안의 다른 날을 쓴다.
        LocalDate verifiedDate = periodStart.isEqual(today) ? periodStart.plusDays(1) : periodStart;
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc)
                .participationRound(mc.getParticipationRound())
                .verifiedDate(verifiedDate)
                .periodStartDate(periodStart)
                .verifiedAt(verifiedDate.atTime(9, 0))
                .imageUrl("challenge-verifications/seed-" + seq.incrementAndGet() + ".jpg")
                .content("먼저 올린 인증")
                .build();
        if (deleted) {
            v.softDelete(LocalDateTime.now(TimeUtil.KST));
        }
        em.persist(v);
        em.flush();
        return v;
    }

    private ChallengeVerificationReqDTO.Verify request() {
        return new ChallengeVerificationReqDTO.Verify(STAGING_KEY, "오늘도 했어요");
    }

    private void blockedWithoutCost(RoutineCycle cycle, ChallengeVerificationErrorCode expected) {
        Member m = member();
        Challenge c = challenge(cycle);
        MemberChallenge mc = join(m, c);
        seed(mc, cycle.currentPeriodStart(LocalDate.now(TimeUtil.KST)), false);

        assertThatThrownBy(() -> challengeVerificationService.verify(m.getId(), c.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", expected);

        verify(reviewClient, never()).review(any(), any(), any());
        verify(mediaService, never()).promote(any(), any(), any());
    }

    @Test
    @DisplayName("주간: 이번 주에 이미 했으면 심사도 승격도 하지 않는다")
    void weekly_BlocksBeforeReviewAndPromote() {
        blockedWithoutCost(RoutineCycle.WEEKLY,
                ChallengeVerificationErrorCode.ALREADY_VERIFIED_IN_PERIOD);
    }

    @Test
    @DisplayName("월간: 이번 달에 이미 했으면 심사도 승격도 하지 않는다")
    void monthly_BlocksBeforeReviewAndPromote() {
        blockedWithoutCost(RoutineCycle.MONTHLY,
                ChallengeVerificationErrorCode.ALREADY_VERIFIED_IN_PERIOD);
    }

    @Test
    @DisplayName("일간도 같다 — 오늘 살아 있는 인증이 있으면 덮어쓰지 않고 막는다")
    void daily_BlocksToo() {
        // 저장 쪽 설명에는 한동안 "DAILY 는 덮어쓴다" 고 적혀 있었지만, 실제 코드는 살아 있는
        // 인증이 있으면 주기와 무관하게 막는다. 덮어쓰기는 본인이 지웠을 때만 일어난다.
        blockedWithoutCost(RoutineCycle.DAILY,
                ChallengeVerificationErrorCode.ALREADY_VERIFIED_TODAY);
    }

    @Test
    @DisplayName("지운 인증은 막지 않는다 — 선검사가 재인증까지 잡으면 안 된다")
    void deleted_DoesNotBlock() {
        when(mediaService.loadForReview(eq(STAGING_KEY), anyInt()))
                .thenReturn(MediaImageLoad.loaded(
                        new MediaImage(new byte[] {1, 2, 3}, "image/jpeg", "etag-precheck")));
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);

        Member m = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        MemberChallenge mc = join(m, c);
        seed(mc, RoutineCycle.WEEKLY.currentPeriodStart(LocalDate.now(TimeUtil.KST)), true);

        assertThat(challengeVerificationService.verify(m.getId(), c.getId(), request()))
                .as("지운 인증은 그 구간을 다시 열어 준다").isNotNull();

        verify(mediaService).promote(any(), any(), any());
    }
}
