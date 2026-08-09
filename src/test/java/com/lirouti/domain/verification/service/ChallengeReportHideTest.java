package com.lirouti.domain.verification.service;

import com.lirouti.domain.verification.service.query.ChallengeVerificationQueryService;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.properties.ChallengeReportProperties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 신고 누적 자동 숨김.
 *
 * 가리는 것은 <b>노출뿐</b>이라, "안 보이게 됐는가"와 "수행 기록이 그대로인가"를 같이 본다.
 * 앞만 검증하면 스트릭까지 지워도 통과한다.
 */
@SpringBootTest
@Transactional
@DisplayName("신고 누적 자동 숨김 테스트")
class ChallengeReportHideTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY =
            "challenge-verifications/2026/07/31/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";
    private static final int BIG = 100;

    @Autowired
    private ChallengeVerificationService challengeVerificationService;
    @Autowired
    private ChallengeVerificationQueryService challengeVerificationQueryService;
    // 상세의 인증 게시글 수는 챌린지 응답의 일부라 조회가 challenge 에 남아 있다.
    @Autowired
    private ChallengeQueryService challengeQueryService;
    @Autowired
    private ChallengeReportProperties reportProperties;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("hide" + n + "@ex.com").nickname("hide" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("hide-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge() {
        Challenge c = Challenge.builder()
                .name("숨김챌린지" + seq.incrementAndGet())
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge join(Member m, Challenge c) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        return mc;
    }

    private ChallengeVerification verification(MemberChallenge mc) {
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(LocalDate.now(KST)).verifiedAt(LocalDateTime.now())
                .imageUrl(KEY).content("신고 대상").build();
        em.persist(v);
        em.flush();
        return v;
    }

    /** 서로 다른 회원이 n번 신고한다. */
    private void reportBy(int count, Challenge c, ChallengeVerification v) {
        for (int i = 0; i < count; i++) {
            challengeVerificationService.report(
                    member().getId(), c.getId(), v.getId(),
                    new ChallengeVerificationReqDTO.Report("부적절한 사진"));
        }
        em.flush();
        em.clear();
    }

    // ── 테스트 ──
    @Test
    @DisplayName("임계값에 도달하면 피드에서 사라진다")
    void report_ReachesThreshold_HiddenFromFeed() {
        // given
        Challenge c = challenge();
        MemberChallenge mc = join(member(), c);
        ChallengeVerification v = verification(mc);
        Member viewer = member();

        // when
        reportBy(reportProperties.getHideThreshold(), c, v);

        // then
        assertThat(challengeVerificationQueryService.getVerificationFeed(c.getId(), viewer.getId(), null, BIG)
                .verifications())
                .isEmpty();
    }

    @Test
    @DisplayName("임계값 미만이면 그대로 보인다")
    void report_BelowThreshold_StaysVisible() {
        // given
        Challenge c = challenge();
        MemberChallenge mc = join(member(), c);
        ChallengeVerification v = verification(mc);
        Member viewer = member();

        // when
        reportBy(reportProperties.getHideThreshold() - 1, c, v);

        // then
        assertThat(challengeVerificationQueryService.getVerificationFeed(c.getId(), viewer.getId(), null, BIG)
                .verifications())
                .extracting(ChallengeVerificationResDTO.FeedItem::verificationId)
                .containsExactly(v.getId());
    }

    @Test
    @DisplayName("작성자 본인의 내 인증 목록에서도 사라진다 — 본인 예외를 두지 않는다")
    void report_ReachesThreshold_HiddenFromAuthorsOwnList() {
        // given
        Challenge c = challenge();
        Member author = member();
        MemberChallenge mc = join(author, c);
        ChallengeVerification v = verification(mc);

        // when
        reportBy(reportProperties.getHideThreshold(), c, v);

        // then
        assertThat(challengeVerificationQueryService.getMyVerifications(author.getId(), c.getId(), null, BIG, null)
                .verifications())
                .isEmpty();
    }

    @Test
    @DisplayName("가려져도 스트릭은 그대로다 — 노출만 막고 수행 기록은 건드리지 않는다")
    void report_ReachesThreshold_StreakUnchanged() {
        // given
        Challenge c = challenge();
        Member author = member();
        MemberChallenge mc = join(author, c);
        ChallengeVerification v = verification(mc);
        int before = mc.getCurrentStreak();

        // when
        reportBy(reportProperties.getHideThreshold(), c, v);

        // then
        MemberChallenge reloaded = em.find(MemberChallenge.class, mc.getId());
        assertThat(reloaded.getCurrentStreak()).isEqualTo(before);
    }

    @Test
    @DisplayName("가려진 인증은 인증 게시글 수에서 빠진다 — 목록과 피드가 어긋나지 않게")
    void report_ReachesThreshold_ExcludedFromPostCount() {
        // given
        Challenge c = challenge();
        MemberChallenge mc = join(member(), c);
        ChallengeVerification v = verification(mc);
        Member viewer = member();
        long before = challengeQueryService.getChallenge(c.getId(), viewer.getId())
                .verificationPostCount();

        // when
        reportBy(reportProperties.getHideThreshold(), c, v);

        // then
        assertThat(before).isEqualTo(1L);
        assertThat(challengeQueryService.getChallenge(c.getId(), viewer.getId())
                .verificationPostCount())
                .isZero();
    }

    @Test
    @DisplayName("임계값을 넘겨 더 신고해도 처음 가려진 시각을 덮어쓰지 않는다")
    void report_BeyondThreshold_KeepsFirstHiddenAt() {
        // given
        Challenge c = challenge();
        MemberChallenge mc = join(member(), c);
        ChallengeVerification v = verification(mc);
        reportBy(reportProperties.getHideThreshold(), c, v);
        LocalDateTime first = em.find(ChallengeVerification.class, v.getId()).getHiddenAt();

        // when
        reportBy(1, c, v);

        // then
        assertThat(em.find(ChallengeVerification.class, v.getId()).getHiddenAt()).isEqualTo(first);
    }
}
