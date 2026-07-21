package com.lirouti.domain.challenge.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

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
            m.applyVerification(TODAY);
            assertThat(m.getCurrentStreak()).isEqualTo(1);
            assertThat(m.getLastVerifiedDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("어제 인증했으면 +1 한다")
        void yesterdayContinues() {
            MemberChallenge m = mc(3, YESTERDAY);
            m.applyVerification(TODAY);
            assertThat(m.getCurrentStreak()).isEqualTo(4);
            assertThat(m.getLastVerifiedDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("오늘 이미 인증했으면 그대로 둔다(중복 인증)")
        void todayUnchanged() {
            MemberChallenge m = mc(3, TODAY);
            m.applyVerification(TODAY);
            assertThat(m.getCurrentStreak()).isEqualTo(3);
        }

        @Test
        @DisplayName("어제보다 오래 전에 인증했으면 1로 리셋한다")
        void staleResetsToOne() {
            MemberChallenge m = mc(9, TWO_DAYS_AGO);
            m.applyVerification(TODAY);
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
            assertThat(mc(0, null).currentStreakAsOf(TODAY)).isZero();
        }

        @Test
        @DisplayName("오늘 인증했으면 저장된 스트릭을 그대로 인정한다")
        void todayKeeps() {
            assertThat(mc(5, TODAY).currentStreakAsOf(TODAY)).isEqualTo(5);
        }

        @Test
        @DisplayName("어제 인증(오늘 아직 안 함)이면 아직 끊기지 않았으므로 유지한다")
        void yesterdayKeeps() {
            assertThat(mc(5, YESTERDAY).currentStreakAsOf(TODAY)).isEqualTo(5);
        }

        @Test
        @DisplayName("어제보다 오래됐으면 끊긴 것이므로 0 (저장값 무시)")
        void staleIsZero() {
            assertThat(mc(5, TWO_DAYS_AGO).currentStreakAsOf(TODAY)).isZero();
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
}
