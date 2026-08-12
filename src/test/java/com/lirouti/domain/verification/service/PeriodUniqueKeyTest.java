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
 * <b>주기 1회를 DB 가 보장한다</b>는 주장을 실제로 검증한다.
 *
 * <p>기존 주간·월간 테스트는 전부 <b>애플리케이션 분기에서 끝난다.</b> 저장 경로가 "이번 구간에
 * 인증이 있나" 를 먼저 조회해 막으므로 INSERT 까지 가지 않고, 그래서 <b>유니크 키를 한 번도
 * 건드리지 않는다.</b> 키를 잘못 걸었어도, 심지어 마이그레이션에서 {@code ADD UNIQUE KEY} 를
 * 빼먹어도 그 테스트들은 전부 통과한다.
 *
 * <p>이 프로젝트는 인증의 동시성을 유니크 제약에 맡긴다(database-schema.md). 애플리케이션
 * 검사는 잠금 없이 도는 선검사이고 최종 판정은 DB 가 한다. 그 최종 방어선을 여기서 본다.
 *
 * <p><b>선검사를 우회하려고 저장 경로를 쓰지 않는다.</b> 서비스를 부르면 분기에서 끝나 버리므로,
 * 인증 행을 직접 심어 제약이 실제로 막는지 확인한다. 서비스 경로의 동시성은
 * {@code WeeklyVerificationConcurrencyTest} 가 따로 본다.
 *
 * <p>"오늘" 은 고정할 수 없다(저장 경로가 {@code now()} 를 직접 부른다). 그래서 실제 오늘을
 * 기준으로 구간을 계산한다.
 */
@SpringBootTest
@Transactional
@DisplayName("주기 유니크 키 — DB 가 구간 1회를 보장한다")
class PeriodUniqueKeyTest {

    private static final String KEY =
            "challenge-verifications-staging/44444444-4444-4444-8444-444444444444.jpg";

    @Autowired
    private ChallengeVerificationService challengeVerificationService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("puk" + n + "@ex.com").nickname("puk" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("puk-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge(RoutineCycle cycle) {
        Challenge c = Challenge.builder()
                .name("주기키챌린지").category(ChallengeCategory.HEALTH)
                .routineCycle(cycle).active(true).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge join(Member m, Challenge c, int round) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(round).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        return mc;
    }

    /** 구간 첫날을 직접 지정해 인증 행을 만든다. 저장 경로를 거치지 않아 선검사가 없다. */
    private ChallengeVerification verificationRow(
            MemberChallenge mc, LocalDate verifiedDate, LocalDate periodStart) {
        return ChallengeVerification.builder()
                .memberChallenge(mc)
                .participationRound(mc.getParticipationRound())
                .verifiedDate(verifiedDate)
                .periodStartDate(periodStart)
                .verifiedAt(verifiedDate.atTime(9, 0))
                .imageUrl("challenge-verifications/puk-" + seq.incrementAndGet() + ".jpg")
                .content("직접 심은 인증")
                .build();
    }

    /** 회차를 직접 지정한다. 참여 행은 회원·챌린지당 하나뿐이라 회차만 바꿔 심는다. */
    private ChallengeVerification verificationRow(
            MemberChallenge mc, LocalDate verifiedDate, LocalDate periodStart, int round) {
        return ChallengeVerification.builder()
                .memberChallenge(mc)
                .participationRound(round)
                .verifiedDate(verifiedDate)
                .periodStartDate(periodStart)
                .verifiedAt(verifiedDate.atTime(9, 0))
                .imageUrl("challenge-verifications/puk-" + seq.incrementAndGet() + ".jpg")
                .content("직접 심은 인증")
                .build();
    }

    private ChallengeVerification storedOf(MemberChallenge mc) {
        List<ChallengeVerification> rows = em.createQuery(
                        "select v from ChallengeVerification v where v.memberChallenge.id = :id",
                        ChallengeVerification.class)
                .setParameter("id", mc.getId())
                .getResultList();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    // ── 제약이 실제로 막는가 ──

    @Test
    @DisplayName("같은 구간이면 날짜가 달라도 두 번째 INSERT 가 막힌다 — 선검사가 아니라 DB 가")
    void sameePeriodDifferentDate_SecondInsertViolatesUniqueKey() {
        Member m = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        MemberChallenge mc = join(m, c, 1);

        LocalDate today = LocalDate.now(TimeUtil.KST);
        LocalDate weekStart = RoutineCycle.WEEKLY.currentPeriodStart(today);

        // 같은 주의 서로 다른 두 날. period_start_date 는 같다.
        em.persist(verificationRow(mc, weekStart, weekStart));
        em.flush();

        // 식별자 전략이 IDENTITY 라 persist 시점에 INSERT 가 나간다. flush 를 기다리지 않는다.
        assertThatThrownBy(() ->
                em.persist(verificationRow(mc, weekStart.plusDays(1), weekStart)))
                .as("유니크 키가 (참여, 회차, 구간 첫날) 이라 같은 구간의 둘째 행은 못 들어간다")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("구간이 다르면 두 행이 함께 들어간다 — 제약이 과하게 막지 않는지")
    void differentPeriod_BothRowsSurvive() {
        Member m = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        MemberChallenge mc = join(m, c, 1);

        LocalDate today = LocalDate.now(TimeUtil.KST);
        LocalDate thisWeek = RoutineCycle.WEEKLY.currentPeriodStart(today);
        LocalDate lastWeek = RoutineCycle.WEEKLY.previousPeriodStart(today);

        em.persist(verificationRow(mc, thisWeek, thisWeek));
        em.persist(verificationRow(mc, lastWeek, lastWeek));
        em.flush();

        List<ChallengeVerification> rows = em.createQuery(
                        "select v from ChallengeVerification v where v.memberChallenge.id = :id",
                        ChallengeVerification.class)
                .setParameter("id", mc.getId())
                .getResultList();
        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("회차가 다르면 같은 구간도 들어간다 — 재참여로 다시 셀 수 있어야 한다")
    void differentRound_SamePeriodAllowed() {
        Member m = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        LocalDate weekStart = RoutineCycle.WEEKLY.currentPeriodStart(LocalDate.now(TimeUtil.KST));

        // 참여 행은 uk_member_challenge(member_id, challenge_id) 로 하나뿐이다.
        // 재참여는 새 행이 아니라 같은 행의 회차가 오르는 것이므로, 회차만 바꿔 심는다.
        MemberChallenge mc = join(m, c, 1);
        em.persist(verificationRow(mc, weekStart, weekStart, 1));
        em.flush();

        // 유니크 키에 회차가 들어 있어 같은 구간이어도 통과한다.
        em.persist(verificationRow(mc, weekStart, weekStart, 2));
        em.flush();

        assertThat(em.createQuery(
                        "select count(v) from ChallengeVerification v "
                                + "where v.memberChallenge.challenge.id = :cid", Long.class)
                .setParameter("cid", c.getId())
                .getSingleResult())
                .isEqualTo(2L);
    }

    // ── 저장 경로가 무엇을 넣는가 ──

    @Test
    @DisplayName("주간 인증은 그 주 일요일을 구간 첫날로 저장한다")
    void weeklyVerify_StoresSundayAsPeriodStart() {
        Member m = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        MemberChallenge mc = join(m, c, 1);
        em.flush();

        challengeVerificationService.verify(
                m.getId(), c.getId(), new ChallengeVerificationReqDTO.Verify(KEY, "주간 인증"));
        em.flush();

        LocalDate today = LocalDate.now(TimeUtil.KST);
        ChallengeVerification stored = storedOf(mc);
        assertThat(stored.getPeriodStartDate())
                .as("그 주 일요일이어야 한다")
                .isEqualTo(RoutineCycle.WEEKLY.currentPeriodStart(today));
        assertThat(stored.getPeriodStartDate().getDayOfWeek())
                .isEqualTo(java.time.DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("월간 인증은 그 달 1일을 구간 첫날로 저장한다")
    void monthlyVerify_StoresFirstDayOfMonthAsPeriodStart() {
        Member m = member();
        Challenge c = challenge(RoutineCycle.MONTHLY);
        MemberChallenge mc = join(m, c, 1);
        em.flush();

        challengeVerificationService.verify(
                m.getId(), c.getId(), new ChallengeVerificationReqDTO.Verify(KEY, "월간 인증"));
        em.flush();

        LocalDate today = LocalDate.now(TimeUtil.KST);
        ChallengeVerification stored = storedOf(mc);
        assertThat(stored.getPeriodStartDate())
                .isEqualTo(today.withDayOfMonth(1));
        assertThat(stored.getPeriodStartDate().getDayOfMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("일간 인증은 인증일을 그대로 구간 첫날로 저장한다 — 옛 키와 값이 같다")
    void dailyVerify_StoresVerifiedDateAsPeriodStart() {
        Member m = member();
        Challenge c = challenge(RoutineCycle.DAILY);
        MemberChallenge mc = join(m, c, 1);
        em.flush();

        challengeVerificationService.verify(
                m.getId(), c.getId(), new ChallengeVerificationReqDTO.Verify(KEY, "일간 인증"));
        em.flush();

        ChallengeVerification stored = storedOf(mc);
        assertThat(stored.getPeriodStartDate()).isEqualTo(stored.getVerifiedDate());
    }

    // ── 마이그레이션이 실제로 키를 만들었는가 ──

    @Test
    @DisplayName("유니크 키가 (참여, 회차, 구간 첫날) 로 걸려 있다 — 옛 키는 남아 있지 않다")
    void schema_HasPeriodUniqueKeyAndNotTheOldOne() {
        @SuppressWarnings("unchecked")
        List<String> columns = em.createNativeQuery("""
                        select column_name
                        from information_schema.statistics
                        where table_schema = database()
                          and table_name = 'challenge_verification'
                          and index_name = 'uk_verification_round_period'
                        order by seq_in_index
                        """)
                .getResultList();

        assertThat(columns)
                .as("마이그레이션이 이 키를 만들지 않았다면 구간 1회는 아무도 보장하지 않는다")
                .containsExactly("member_challenge_id", "participation_round", "period_start_date");

        Number oldKeyCount = (Number) em.createNativeQuery("""
                        select count(*)
                        from information_schema.statistics
                        where table_schema = database()
                          and table_name = 'challenge_verification'
                          and index_name = 'uk_verification_round_date'
                        """)
                .getSingleResult();

        assertThat(oldKeyCount.intValue())
                .as("옛 키가 남아 있으면 날짜 기준 제약이 함께 걸려 주간 인증이 엉뚱하게 막힌다")
                .isZero();
    }
}
