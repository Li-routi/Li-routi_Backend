package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주간·월간 챌린지의 <b>구간 1회</b>가 실제로 막히는지 본다.
 *
 * <p>예전에는 유니크 키가 {@code verified_date} 라 날짜만 다르면 얼마든지 들어갔다. WEEKLY
 * 챌린지에 월·화·수 매일 인증해도 DB 가 막지 않았고, 현재 운영 데이터가 전부 DAILY 라
 * 드러나지 않았을 뿐이다.
 *
 * <p><b>"오늘" 을 고정할 수 없다.</b> 저장 경로가 {@code now()} 를 직접 부르므로 테스트가 날짜를
 * 주입하지 못한다. 그래서 실제 오늘을 기준으로 구간을 계산하고, <b>같은 구간 안의 다른 날</b>에
 * 이미 인증이 있는 상태를 만들어 두고 오늘 인증을 시도한다 — 막히면 구간으로 판정한 것이고,
 * 통과하면 날짜로 판정한 것이다.
 */
@SpringBootTest
@Transactional
@DisplayName("주간·월간 구간 1회")
class WeeklyMonthlyVerificationTest {

    private static final String KEY =
            "challenge-verifications-staging/33333333-3333-4333-8333-333333333333.jpg";

    @Autowired
    private ChallengeVerificationService challengeVerificationService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("wm" + n + "@ex.com").nickname("wm" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("wm-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge(RoutineCycle cycle) {
        Challenge c = Challenge.builder()
                .name("주기챌린지").category(ChallengeCategory.HEALTH)
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

    /** 구간 첫날을 직접 지정해 인증 행을 심는다. 저장 경로를 거치지 않으므로 심사·승격이 없다. */
    private ChallengeVerification seedVerification(
            MemberChallenge mc, LocalDate verifiedDate, LocalDate periodStart) {
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc)
                .participationRound(mc.getParticipationRound())
                .verifiedDate(verifiedDate)
                .periodStartDate(periodStart)
                .verifiedAt(verifiedDate.atTime(9, 0))
                .imageUrl("challenge-verifications/seed-" + seq.incrementAndGet() + ".jpg")
                .content("먼저 올린 인증")
                .build();
        em.persist(v);
        em.flush();
        return v;
    }

    private List<ChallengeVerification> rowsOf(MemberChallenge mc) {
        return em.createQuery(
                        "select v from ChallengeVerification v where v.memberChallenge.id = :id",
                        ChallengeVerification.class)
                .setParameter("id", mc.getId())
                .getResultList();
    }

    private ChallengeVerificationReqDTO.Verify request() {
        return new ChallengeVerificationReqDTO.Verify(KEY, "오늘도 했어요");
    }

    /**
     * 그 구간 안에서 <b>오늘이 아닌 날</b>을 고른다.
     *
     * <p>구간 첫날을 그대로 쓰면 <b>테스트가 무의미해지는 날이 있다.</b> 오늘이 일요일이면
     * 그 주의 첫날이 곧 오늘이고, 매월 1일이면 그 달의 첫날이 곧 오늘이다. 그런 날에는
     * {@code period_start_date} 대신 {@code verified_date} 를 보는 <b>틀린 구현도 이 테스트를
     * 통과한다</b> — 두 컬럼 값이 같아지기 때문이다.
     *
     * <p>그래서 첫날이 오늘이면 하루 뒤를 쓴다. 같은 구간 안이므로 판정 결과는 같고,
     * 두 컬럼 값만 갈라진다.
     */
    private LocalDate seedDateIn(LocalDate periodStart, LocalDate today) {
        LocalDate seed = periodStart.isEqual(today) ? periodStart.plusDays(1) : periodStart;
        assertThat(seed)
                .as("구간 판정을 검증하려면 인증일이 오늘과 달라야 한다")
                .isNotEqualTo(today);
        return seed;
    }

    @Test
    @DisplayName("주간: 이번 주에 이미 했으면 다른 날이어도 막힌다")
    void weekly_SameWeekIsBlocked() {
        // given: 이번 주의 다른 날에 이미 인증이 있다.
        LocalDate today = LocalDate.now(TimeUtil.KST);
        LocalDate weekStart = RoutineCycle.WEEKLY.currentPeriodStart(today);

        Member me = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        MemberChallenge mc = join(me, c);
        // 인증일은 오늘과 다르게, 구간 첫날은 이번 주 일요일로 심는다.
        // 두 값이 갈려 있어야 period_start_date 로 판정한다는 것이 증명된다.
        seedVerification(mc, seedDateIn(weekStart, today), weekStart);

        // when & then: 날짜는 다른데 같은 주라 막혀야 한다.
        // 예외 종류까지 본다 — RuntimeException 으로만 두면 S3·심사 단계에서 죽어도 통과한다.
        assertThatThrownBy(() ->
                challengeVerificationService.verify(me.getId(), c.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code",
                        ChallengeVerificationErrorCode.ALREADY_VERIFIED_IN_PERIOD);

        // 행이 늘지 않았다 — 덮어쓰기도 아니고 새 행도 아니다.
        assertThat(rowsOf(mc)).hasSize(1);
    }

    @Test
    @DisplayName("주간: 지난 주 인증은 이번 주를 막지 않는다")
    void weekly_PreviousWeekDoesNotBlock() {
        LocalDate today = LocalDate.now(TimeUtil.KST);
        LocalDate previousWeekStart = RoutineCycle.WEEKLY.previousPeriodStart(today);

        Member me = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        MemberChallenge mc = join(me, c);
        seedVerification(mc, previousWeekStart, previousWeekStart);

        // 지난 주 것은 이번 주를 막지 않으므로 새 행이 생긴다.
        challengeVerificationService.verify(me.getId(), c.getId(), request());

        assertThat(rowsOf(mc)).hasSize(2);
    }

    @Test
    @DisplayName("월간: 이번 달에 이미 했으면 다른 날이어도 막힌다")
    void monthly_SameMonthIsBlocked() {
        LocalDate today = LocalDate.now(TimeUtil.KST);
        LocalDate monthStart = RoutineCycle.MONTHLY.currentPeriodStart(today);

        Member me = member();
        Challenge c = challenge(RoutineCycle.MONTHLY);
        MemberChallenge mc = join(me, c);
        seedVerification(mc, seedDateIn(monthStart, today), monthStart);

        assertThatThrownBy(() ->
                challengeVerificationService.verify(me.getId(), c.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code",
                        ChallengeVerificationErrorCode.ALREADY_VERIFIED_IN_PERIOD);

        assertThat(rowsOf(mc)).hasSize(1);
    }

    @Test
    @DisplayName("주간은 덮어쓰기가 없다 — 사진 교체가 구간 판정을 뚫는 길이 되면 안 된다")
    void weekly_DoesNotOverwrite() {
        LocalDate today = LocalDate.now(TimeUtil.KST);
        LocalDate weekStart = RoutineCycle.WEEKLY.currentPeriodStart(today);

        Member me = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        MemberChallenge mc = join(me, c);
        ChallengeVerification seeded = seedVerification(mc, seedDateIn(weekStart, today), weekStart);
        String originalKey = seeded.getImageUrl();

        assertThatThrownBy(() ->
                challengeVerificationService.verify(me.getId(), c.getId(), request()))
                .isInstanceOf(VerificationException.class);

        em.flush();
        em.clear();

        // 사진이 그대로여야 한다. DAILY 였다면 여기서 새 key 로 덮였을 것이다.
        assertThat(em.find(ChallengeVerification.class, seeded.getId()).getImageUrl())
                .isEqualTo(originalKey);
    }

    @Test
    @DisplayName("DAILY 는 그대로 덮어쓴다 — 이 변경이 당일 재인증을 건드리지 않았다")
    void daily_StillOverwrites() {
        LocalDate today = LocalDate.now(TimeUtil.KST);

        Member me = member();
        Challenge c = challenge(RoutineCycle.DAILY);
        MemberChallenge mc = join(me, c);
        seedVerification(mc, today, today);

        // 막히지 않고 덮어쓴다. 예외가 나면 여기서 그대로 터진다.
        challengeVerificationService.verify(me.getId(), c.getId(), request());

        // 행이 늘지 않는다 — 하루 한 행이라는 성질은 그대로다.
        assertThat(rowsOf(mc)).hasSize(1);
    }
}
