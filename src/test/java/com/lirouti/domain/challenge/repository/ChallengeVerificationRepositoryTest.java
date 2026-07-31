package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.ChallengeVerificationReport;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("ChallengeVerificationRepository 피드 조회 테스트")
class ChallengeVerificationRepositoryTest {

    private static final int BIG = 100;

    @Autowired
    private ChallengeVerificationRepository challengeVerificationRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    // ── 헬퍼 ──
    private Member member(boolean active) {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("vfeed-m" + n + "@ex.com").nickname("nick" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("vfeed-sid-" + n).build();
        if (!active) {
            ReflectionTestUtils.setField(m, "isActive", false);
        }
        em.persist(m);
        return m;
    }

    private Challenge challenge() {
        Challenge c = Challenge.builder()
                .name("vfeed챌린지").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge join(Member m, Challenge c, int round, boolean active) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(round).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(active).build();
        em.persist(mc);
        return mc;
    }

    private ChallengeVerification verify(MemberChallenge mc, LocalDate date, int round) {
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc)
                .participationRound(round)
                .verifiedDate(date)
                .verifiedAt(date.atTime(9, 0))
                .imageUrl("challenge-verifications/key-" + seq.incrementAndGet() + ".jpg")
                .content("인증")
                .build();
        em.persist(v);
        return v;
    }

    private ChallengeVerificationReport report(ChallengeVerification v, Member reporter) {
        ChallengeVerificationReport r = ChallengeVerificationReport.builder()
                .challengeVerification(v).reporter(reporter).reason("부적절한 사진").build();
        em.persist(r);
        return r;
    }

    // ── 테스트 ──
    @Test
    @DisplayName("내가 신고한 인증은 내 피드에서만 빠지고, 다른 회원의 피드에는 그대로 보인다")
    void findFeedByCursor_ExcludesOnlyViewersOwnReports() {
        Challenge c = challenge();
        MemberChallenge mc = join(member(true), c, 1, true);
        ChallengeVerification kept = verify(mc, LocalDate.of(2026, 7, 1), 1);
        ChallengeVerification reported = verify(mc, LocalDate.of(2026, 7, 2), 1);

        Member reporter = member(true);
        Member bystander = member(true);
        report(reported, reporter);
        em.flush();

        // 신고자 본인: 신고한 인증만 빠진다.
        assertThat(challengeVerificationRepository.findFeedByCursor(c.getId(), reporter.getId(), null, BIG))
                .extracting(ChallengeVerification::getId)
                .containsExactly(kept.getId());

        // 다른 회원: 신고와 무관하게 둘 다 보인다. 신고는 삭제가 아니다.
        assertThat(challengeVerificationRepository.findFeedByCursor(c.getId(), bystander.getId(), null, BIG))
                .extracting(ChallengeVerification::getId)
                .containsExactly(reported.getId(), kept.getId());

        // viewerId가 없으면 필터를 걸지 않는다.
        assertThat(challengeVerificationRepository.findFeedByCursor(c.getId(), null, null, BIG))
                .hasSize(2);
    }

    @Test
    @DisplayName("신고로 걸러진 뒤에도 요청한 개수(limit)만큼 채워 내려준다")
    void findFeedByCursor_FillsLimitAfterExcludingReports() {
        Challenge c = challenge();
        MemberChallenge mc = join(member(true), c, 1, true);
        ChallengeVerification v1 = verify(mc, LocalDate.of(2026, 7, 1), 1);
        ChallengeVerification v2 = verify(mc, LocalDate.of(2026, 7, 2), 1);
        ChallengeVerification v3 = verify(mc, LocalDate.of(2026, 7, 3), 1);

        Member reporter = member(true);
        report(v3, reporter);   // 가장 최신 것을 신고
        em.flush();

        // 신고분을 제외한 뒤 최신 2건. 필터가 limit 이후가 아니라 이전에 적용돼야 한다.
        assertThat(challengeVerificationRepository.findFeedByCursor(c.getId(), reporter.getId(), null, 2))
                .extracting(ChallengeVerification::getId)
                .containsExactly(v2.getId(), v1.getId());
    }

    @Test
    @DisplayName("최신순(id 내림차순)으로 내려주고, 다른 챌린지의 인증은 섞이지 않는다")
    void findFeedByCursor_OrdersByIdDescAndScopesToChallenge() {
        Challenge target = challenge();
        Challenge otherChallenge = challenge();
        MemberChallenge mc = join(member(true), target, 1, true);
        MemberChallenge otherMc = join(member(true), otherChallenge, 1, true);

        ChallengeVerification first = verify(mc, LocalDate.of(2026, 7, 1), 1);
        ChallengeVerification second = verify(mc, LocalDate.of(2026, 7, 2), 1);
        verify(otherMc, LocalDate.of(2026, 7, 3), 1);
        em.flush();

        List<ChallengeVerification> feed =
                challengeVerificationRepository.findFeedByCursor(target.getId(), null, null, BIG);

        assertThat(feed).extracting(ChallengeVerification::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("커서를 주면 그보다 이전(작은 id) 인증만 가져온다")
    void findFeedByCursor_WithCursor_ReturnsOlderOnly() {
        Challenge c = challenge();
        MemberChallenge mc = join(member(true), c, 1, true);
        ChallengeVerification oldest = verify(mc, LocalDate.of(2026, 7, 1), 1);
        ChallengeVerification middle = verify(mc, LocalDate.of(2026, 7, 2), 1);
        ChallengeVerification newest = verify(mc, LocalDate.of(2026, 7, 3), 1);
        em.flush();

        List<ChallengeVerification> feed =
                challengeVerificationRepository.findFeedByCursor(c.getId(), null, newest.getId(), BIG);

        assertThat(feed).extracting(ChallengeVerification::getId)
                .containsExactly(middle.getId(), oldest.getId());
    }

    @Test
    @DisplayName("limit만큼만 가져온다 (hasNext 판단용 size+1 조회)")
    void findFeedByCursor_RespectsLimit() {
        Challenge c = challenge();
        MemberChallenge mc = join(member(true), c, 1, true);
        verify(mc, LocalDate.of(2026, 7, 1), 1);
        verify(mc, LocalDate.of(2026, 7, 2), 1);
        verify(mc, LocalDate.of(2026, 7, 3), 1);
        em.flush();

        assertThat(challengeVerificationRepository.findFeedByCursor(c.getId(), null, null, 2)).hasSize(2);
    }

    @Test
    @DisplayName("탈퇴 회원의 인증은 피드에서 제외한다")
    void findFeedByCursor_ExcludesWithdrawnMember() {
        Challenge c = challenge();
        MemberChallenge active = join(member(true), c, 1, true);
        MemberChallenge withdrawn = join(member(false), c, 1, true);
        ChallengeVerification visible = verify(active, LocalDate.of(2026, 7, 1), 1);
        verify(withdrawn, LocalDate.of(2026, 7, 2), 1);
        em.flush();

        List<ChallengeVerification> feed =
                challengeVerificationRepository.findFeedByCursor(c.getId(), null, null, BIG);

        assertThat(feed).extracting(ChallengeVerification::getId).containsExactly(visible.getId());
    }

    @Test
    @DisplayName("그만둔 참여의 인증도 피드에 남는다 (인증 이벤트는 사라지지 않는다)")
    void findFeedByCursor_IncludesVerificationsOfLeftParticipation() {
        Challenge c = challenge();
        MemberChallenge left = join(member(true), c, 1, false);
        ChallengeVerification v = verify(left, LocalDate.of(2026, 7, 1), 1);
        em.flush();

        assertThat(challengeVerificationRepository.findFeedByCursor(c.getId(), null, null, BIG))
                .extracting(ChallengeVerification::getId).containsExactly(v.getId());
    }

    @Test
    @DisplayName("같은 날 이탈 후 재참여해 다시 인증하면 회차가 다른 두 건이 모두 보인다")
    void findFeedByCursor_KeepsBothRoundsOfSameDay() {
        Challenge c = challenge();
        Member m = member(true);
        MemberChallenge mc = join(m, c, 1, true);
        LocalDate sameDay = LocalDate.of(2026, 7, 1);
        ChallengeVerification round1 = verify(mc, sameDay, 1);

        // 이탈 후 재참여 — 같은 행의 회차만 올라간다.
        mc.leave();
        mc.rejoin(LocalDateTime.now());
        ChallengeVerification round2 = verify(mc, sameDay, mc.getParticipationRound());
        em.flush();

        // 인증(게시글) 단위 나열이므로 중복 제거를 하지 않는다.
        assertThat(challengeVerificationRepository.findFeedByCursor(c.getId(), null, null, BIG))
                .extracting(ChallengeVerification::getId)
                .containsExactly(round2.getId(), round1.getId());
    }

    @Test
    @DisplayName("현재 회차의 오늘 인증 행을 찾는다 (당일 재인증 판단용)")
    void findByRoundAndDate_ReturnsCurrentRoundRowOnly() {
        Challenge c = challenge();
        MemberChallenge mc = join(member(true), c, 2, true);
        LocalDate today = LocalDate.of(2026, 7, 10);
        ChallengeVerification previousRound = verify(mc, today, 1);
        ChallengeVerification currentRound = verify(mc, today, 2);
        em.flush();

        assertThat(challengeVerificationRepository
                .findByMemberChallengeIdAndParticipationRoundAndVerifiedDate(mc.getId(), 2, today))
                .get().extracting(ChallengeVerification::getId).isEqualTo(currentRound.getId());

        assertThat(challengeVerificationRepository
                .findByMemberChallengeIdAndParticipationRoundAndVerifiedDate(mc.getId(), 1, today))
                .get().extracting(ChallengeVerification::getId).isEqualTo(previousRound.getId());
    }
}
