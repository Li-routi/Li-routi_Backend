package com.lirouti.domain.challenge.entity;

import com.lirouti.domain.challenge.enums.RoutineCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemberChallenge 스트릭 도메인 로직")
class MemberChallengeStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TWO_DAYS_AGO = TODAY.minusDays(2);

    private MemberChallenge mc(int streak, LocalDate lastVerified) {
        return MemberChallenge.builder()
                .currentStreak(streak)
                .lastVerifiedDate(lastVerified)
                .build();
    }

    @Nested
    @DisplayName("applyVerification: 인증 시 스트릭 갱신")
    class ApplyVerification {

        @Test
        @DisplayName("첫 인증(마지막 인증일 없음)은 스트릭을 1로 시작한다")
        void firstVerification() {
            MemberChallenge m = mc(0, null);
            m.applyVerification(TODAY, RoutineCycle.DAILY);
            assertThat(m.getCurrentStreak()).isEqualTo(1);
            assertThat(m.getLastVerifiedDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("어제 인증했으면 +1 한다")
        void yesterdayContinues() {
            MemberChallenge m = mc(3, YESTERDAY);
            m.applyVerification(TODAY, RoutineCycle.DAILY);
            assertThat(m.getCurrentStreak()).isEqualTo(4);
            assertThat(m.getLastVerifiedDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("오늘 이미 인증했으면 그대로 둔다(중복 인증)")
        void todayUnchanged() {
            MemberChallenge m = mc(3, TODAY);
            m.applyVerification(TODAY, RoutineCycle.DAILY);
            assertThat(m.getCurrentStreak()).isEqualTo(3);
        }

        @Test
        @DisplayName("어제보다 오래 전에 인증했으면 1로 리셋한다")
        void staleResetsToOne() {
            MemberChallenge m = mc(9, TWO_DAYS_AGO);
            m.applyVerification(TODAY, RoutineCycle.DAILY);
            assertThat(m.getCurrentStreak()).isEqualTo(1);
            assertThat(m.getLastVerifiedDate()).isEqualTo(TODAY);
        }
    }

    @Nested
    @DisplayName("currentStreakAsOf: 조회 시점 유효 스트릭 판정")
    class CurrentStreakAsOf {

        @Test
        @DisplayName("마지막 인증일이 없으면 0")
        void nullIsZero() {
            assertThat(mc(0, null).currentStreakAsOf(TODAY, RoutineCycle.DAILY)).isZero();
        }

        @Test
        @DisplayName("오늘 인증했으면 저장된 스트릭을 그대로 인정한다")
        void todayKeeps() {
            assertThat(mc(5, TODAY).currentStreakAsOf(TODAY, RoutineCycle.DAILY)).isEqualTo(5);
        }

        @Test
        @DisplayName("어제 인증(오늘 아직 안 함)이면 아직 끊기지 않았으므로 유지한다")
        void yesterdayKeeps() {
            assertThat(mc(5, YESTERDAY).currentStreakAsOf(TODAY, RoutineCycle.DAILY)).isEqualTo(5);
        }

        @Test
        @DisplayName("어제보다 오래됐으면 끊긴 것이므로 0 (저장값 무시)")
        void staleIsZero() {
            assertThat(mc(5, TWO_DAYS_AGO).currentStreakAsOf(TODAY, RoutineCycle.DAILY)).isZero();
        }
    }

    @Nested
    @DisplayName("참여 상태 전이")
    class Participation {

        @Test
        @DisplayName("leave는 참여 상태만 끈다")
        void leave() {
            MemberChallenge m = MemberChallenge.builder().active(true).build();
            m.leave();
            assertThat(m.isParticipating()).isFalse();
        }

        @Test
        @DisplayName("rejoin은 회차를 올리고 스트릭·마지막 인증일을 초기화하며 다시 참여로 바꾼다")
        void rejoin() {
            MemberChallenge m = MemberChallenge.builder()
                    .participationRound(1).currentStreak(7)
                    .lastVerifiedDate(YESTERDAY).active(false)
                    .build();

            m.rejoin(LocalDateTime.of(2026, 7, 20, 9, 0));

            assertThat(m.isParticipating()).isTrue();
            assertThat(m.getParticipationRound()).isEqualTo(2);
            assertThat(m.getCurrentStreak()).isZero();
            assertThat(m.getLastVerifiedDate()).isNull();
        }
    }

    @Nested
    @DisplayName("주기 단위 스트릭 — DAILY 가 아니면 '연속'의 뜻이 달라진다")
    class PeriodicStreak {

        // 2026-08-02(일) ~ 08-08(토) 이 한 주다.
        private static final LocalDate MON_W1 = LocalDate.parse("2026-08-03");
        private static final LocalDate WED_W1 = LocalDate.parse("2026-08-05");
        private static final LocalDate SUN_W2 = LocalDate.parse("2026-08-09");
        private static final LocalDate SAT_W2 = LocalDate.parse("2026-08-15");
        private static final LocalDate WED_W4 = LocalDate.parse("2026-08-26");

        @Test
        @DisplayName("주간: 같은 주에 또 인증해도 연속이 늘지 않는다")
        void weekly_SameWeekKeepsStreak() {
            MemberChallenge mc = mc(1, MON_W1);

            mc.applyVerification(WED_W1, RoutineCycle.WEEKLY);

            assertThat(mc.getCurrentStreak()).isEqualTo(1);
            // 같은 구간이면 마지막 인증일도 건드리지 않는다.
            assertThat(mc.getLastVerifiedDate()).isEqualTo(MON_W1);
        }

        @Test
        @DisplayName("주간: 다음 주에 인증하면 연속 2주가 된다 — 날짜는 6일 차이여도 상관없다")
        void weekly_NextWeekIncrements() {
            MemberChallenge mc = mc(1, MON_W1);

            mc.applyVerification(SUN_W2, RoutineCycle.WEEKLY);

            assertThat(mc.getCurrentStreak()).isEqualTo(2);
            assertThat(mc.getLastVerifiedDate()).isEqualTo(SUN_W2);
        }

        @Test
        @DisplayName("주간: 한 주를 통째로 걸렀으면 1로 다시 시작한다")
        void weekly_SkippedWeekResets() {
            MemberChallenge mc = mc(5, WED_W1);

            mc.applyVerification(WED_W4, RoutineCycle.WEEKLY);

            assertThat(mc.getCurrentStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("주간: 이번 주에 아직 안 했어도 지난 주에 했으면 끊기지 않았다")
        void weekly_PreviousWeekStillAlive() {
            MemberChallenge mc = mc(3, WED_W1);

            assertThat(mc.currentStreakAsOf(SAT_W2, RoutineCycle.WEEKLY)).isEqualTo(3);
        }

        @Test
        @DisplayName("주간: 두 주 넘게 비면 저장값과 무관하게 0이다")
        void weekly_TwoWeeksGapIsBroken() {
            MemberChallenge mc = mc(9, WED_W1);

            assertThat(mc.currentStreakAsOf(WED_W4, RoutineCycle.WEEKLY)).isZero();
        }

        @Test
        @DisplayName("주간 인증을 일간 셈법으로 보면 끊긴 것으로 나온다 — 주기를 넘기는 이유")
        void weekly_MisreadAsDailyBreaks() {
            MemberChallenge mc = mc(3, WED_W1);

            assertThat(mc.currentStreakAsOf(SUN_W2, RoutineCycle.WEEKLY)).isEqualTo(3);
            assertThat(mc.currentStreakAsOf(SUN_W2, RoutineCycle.DAILY)).isZero();
        }

        @Test
        @DisplayName("월간: 지난 달에 했으면 연속이 이어진다 — 달 길이가 달라도 같다")
        void monthly_PreviousMonthIncrements() {
            MemberChallenge mc = mc(2, LocalDate.parse("2026-02-27"));

            mc.applyVerification(LocalDate.parse("2026-03-01"), RoutineCycle.MONTHLY);

            assertThat(mc.getCurrentStreak()).isEqualTo(3);
        }

        @Test
        @DisplayName("월간: 한 달을 걸렀으면 1로 다시 시작한다")
        void monthly_SkippedMonthResets() {
            MemberChallenge mc = mc(4, LocalDate.parse("2026-01-31"));

            mc.applyVerification(LocalDate.parse("2026-03-01"), RoutineCycle.MONTHLY);

            assertThat(mc.getCurrentStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("재계산도 구간으로 센다 — 같은 주에 두 건이 있어도 1주로 친다")
        void recalculate_CountsByPeriod() {
            MemberChallenge mc = mc(0, null);

            // 같은 주 두 건 + 다음 주 한 건 → 연속 2주
            mc.recalculateStreak(List.of(MON_W1, WED_W1, SUN_W2), RoutineCycle.WEEKLY);

            assertThat(mc.getCurrentStreak()).isEqualTo(2);
            assertThat(mc.getLastVerifiedDate()).isEqualTo(SUN_W2);
        }
    }
}
