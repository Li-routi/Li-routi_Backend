package com.lirouti.domain.challenge.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 상세 화면의 {@code verifiedInCurrentPeriod}.
 *
 * <p>인증하기 버튼의 활성 여부가 이 값으로 갈린다. <b>틀리면 이미 인증한 사람에게 버튼이
 * 열리거나, 아직 안 한 사람의 버튼이 잠긴다.</b> 후자가 특히 나쁘다 — 사용자가 할 수 있는 일을
 * 못 하게 막는 것이라 문의로 이어진다.
 *
 * <p>참여 상태와의 조합을 함께 본다. 이탈했거나 한 번도 참여하지 않았으면 인증할 수 없으므로
 * 이 값도 false 여야 한다.
 */
@SpringBootTest
@Transactional
@DisplayName("상세 인증 여부 플래그 테스트")
class ChallengeDetailVerifiedFlagTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private ChallengeQueryService challengeQueryService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("flag" + n + "@ex.com").nickname("flag" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("flag-sid-" + n).build();
        em.persist(m);
        return m;
    }

    /** 현재 데이터가 전부 DAILY 라 기본값을 그대로 쓴다(엔티티가 null 이면 DAILY 로 채운다). */
    private Challenge challenge() {
        return challenge(null);
    }

    private Challenge challenge(RoutineCycle cycle) {
        Challenge c = Challenge.builder()
                .name("플래그챌린지" + seq.incrementAndGet())
                .category(ChallengeCategory.HEALTH).routineCycle(cycle).active(true).build();
        em.persist(c);
        return c;
    }

    /**
     * 참여시키고, 인증일이 주어지면 <b>실제 인증 행까지</b> 만든다.
     *
     * 판정이 lastVerifiedDate 가 아니라 인증 테이블을 보므로 행이 없으면 false 가 된다.
     * 재참여가 lastVerifiedDate 를 초기화해 그 값으로는 판정할 수 없기 때문이다.
     */
    private MemberChallenge participate(Member m, Challenge c, LocalDate lastVerifiedDate) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(lastVerifiedDate == null ? 0 : 1)
                .lastVerifiedDate(lastVerifiedDate)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        if (lastVerifiedDate != null) {
            em.persist(ChallengeVerification.builder()
                    .memberChallenge(mc).participationRound(1)
                    .verifiedDate(lastVerifiedDate)
                    .verifiedAt(lastVerifiedDate.atTime(9, 0))
                    .imageUrl("challenge-verifications/ffffffff-ffff-4fff-8fff-ffffffffffff.jpg")
                    .content("인증").build());
        }
        em.flush();
        return mc;
    }

    private ChallengeResDTO.Detail detail(Member m, Challenge c) {
        em.flush();
        em.clear();
        return challengeQueryService.getChallenge(c.getId(), m.getId());
    }

    // ── 테스트 ──

    @Test
    @DisplayName("오늘 인증했으면 true — 버튼이 잠긴다")
    void verifiedToday_IsTrue() {
        Member me = member();
        Challenge c = challenge();
        participate(me, c, LocalDate.now(KST));

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isTrue()
        );
    }

    @Test
    @DisplayName("어제가 마지막이면 false — 오늘 다시 인증할 수 있다")
    void verifiedYesterday_IsFalse() {
        Member me = member();
        Challenge c = challenge();
        participate(me, c, LocalDate.now(KST).minusDays(1));

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

    @Test
    @DisplayName("참여만 하고 아직 인증한 적 없으면 false")
    void neverVerified_IsFalse() {
        Member me = member();
        Challenge c = challenge();
        participate(me, c, null);

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

    @Test
    @DisplayName("이탈했으면 오늘 인증 기록이 있어도 false — 인증할 수 없는 상태다")
    void leftChallenge_IsFalse() {
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c, LocalDate.now(KST));
        mc.leave();

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isFalse(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

    @Test
    @DisplayName("참여한 적 없는 회원은 false")
    void notParticipating_IsFalse() {
        Member stranger = member();
        Challenge c = challenge();

        ChallengeResDTO.Detail result = detail(stranger, c);

        assertAll(
                () -> assertThat(result.participating()).isFalse(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

@Test
    @DisplayName("같은 날 이탈 후 재참여해도 true — 하루 1회는 회차를 넘어 적용된다")
    void rejoinedSameDay_StaysTrue() {
        // given: 오늘 인증하고 이탈했다가 같은 날 다시 참여한다.
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c, LocalDate.now(KST));
        mc.leave();
        mc.rejoin(LocalDateTime.now());

        // when
        ChallengeResDTO.Detail result = detail(me, c);

        // then: 예전에는 여기서 false 가 나와 버튼이 다시 열렸다. rejoin 이 lastVerifiedDate 를
        // null 로 만들기 때문인데, 판정이 인증 테이블을 직접 보도록 바뀌어 그 우회가 막혔다.
        // 쓰기도 같은 기준으로 막는다(RejoinDailyOnceTest).
        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isTrue()
        );
    }

    @Test
    @DisplayName("주간 챌린지는 이번 주에 인증했으면 true — 그날 하나만 보면 놓친다")
    void weekly_VerifiedEarlierThisWeek_IsTrue() {
        // given: 이번 주 안의 지난 날에 인증한다.
        // 오늘이 일요일이면 그 주의 지난 날이 없으므로, 있는 경우만 의미가 있다.
        LocalDate today = LocalDate.now(KST);
        LocalDate weekStart = RoutineCycle.WEEKLY.currentPeriodStart(today);
        // 오늘이 주 시작일(일요일)이면 "이번 주 지난 날"이 없어 이 시나리오를 만들 수 없다.
        // return 으로 빠지면 통과한 것처럼 보이므로 건너뜀으로 남긴다 — 실제로 그렇게 만들었다가
        // 회귀를 못 잡았다. 날짜와 무관한 검증은 ChallengeVerificationRepositoryTest 에 있다.
        assumeFalse(weekStart.equals(today), "오늘이 주 시작일이라 이번 주 지난 날이 없다");

        Member me = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        participate(me, c, weekStart);   // 이번 주 첫날에 인증

        // when
        ChallengeResDTO.Detail result = detail(me, c);

        // then: 오늘 인증은 없지만 이번 주에는 했다.
        // 그날 하나만 조회하면 false 가 되어 버튼이 다시 열린다.
        assertAll(
                () -> assertThat(result.routineCycle()).isEqualTo(RoutineCycle.WEEKLY),
                () -> assertThat(result.verifiedInCurrentPeriod()).isTrue()
        );
    }

    @Test
    @DisplayName("주간 챌린지도 지난 주 인증이면 false — 구간이 바뀌면 다시 할 수 있다")
    void weekly_VerifiedLastWeek_IsFalse() {
        Member me = member();
        Challenge c = challenge(RoutineCycle.WEEKLY);
        LocalDate lastWeek = RoutineCycle.WEEKLY.currentPeriodStart(LocalDate.now(KST)).minusDays(1);
        participate(me, c, lastWeek);

        ChallengeResDTO.Detail result = detail(me, c);

        assertThat(result.verifiedInCurrentPeriod()).isFalse();
    }

    @Test
    @DisplayName("월간 챌린지는 이번 달에 인증했으면 true — 주간과 함께 두어 건너뛰는 날을 줄인다")
    void monthly_VerifiedEarlierThisMonth_IsTrue() {
        // 주간 테스트는 오늘이 일요일이면 건너뛴다. 월간은 매달 1일에만 건너뛰므로
        // 둘을 함께 두면 "서비스가 구간 시작을 제대로 넘기는가"가 거의 모든 날 검증된다.
        LocalDate today = LocalDate.now(KST);
        LocalDate monthStart = RoutineCycle.MONTHLY.currentPeriodStart(today);
        assumeFalse(monthStart.equals(today), "오늘이 달 첫날이라 이번 달 지난 날이 없다");

        Member me = member();
        Challenge c = challenge(RoutineCycle.MONTHLY);
        participate(me, c, monthStart);

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.routineCycle()).isEqualTo(RoutineCycle.MONTHLY),
                () -> assertThat(result.verifiedInCurrentPeriod()).isTrue()
        );
    }
}
