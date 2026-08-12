package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DISTINCT_DAY_COUNT 계열(DISTINCT_DAY_COUNT·WEEKLY_DISTINCT_DAY_COUNT·
 * MONTHLY_DISTINCT_DAY_COUNT) 업적이 "서로 다른 날짜"를 세는 근거 테이블.
 *
 * <p>회원이 이 업적에 진행도를 만든 날짜를 하루에 한 행만 남긴다. unique 제약
 * ({@code member_achievement_id, progress_date}) 덕분에 같은 날 같은 업적에 이벤트가
 * 여러 번 들어와도(예: 하루에 루틴을 3개 완료) 두 번째부터는 insert가 실패하고,
 * {@code MemberAchievementProgressDayService} 는 이 실패를 "이미 오늘 치는 세어졌다"는
 * 신호로 써서 진행도를 중복 증가시키지 않는다.
 *
 * <p>기간 제한이 없는 타입(DISTINCT_DAY_COUNT)은 이 테이블의 전체 행 수를, 기간 제한이
 * 있는 타입(WEEKLY/MONTHLY)은 현재 주/월에 속하는 행 수만 세면 된다 — 별도의 "주간
 * 카운터", "월간 카운터"를 각각 두지 않고 원본 날짜 기록 하나로 두 계산을 다 커버한다.
 */
@Entity
@Getter
@Table(
        name = "member_achievement_progress_day",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_achievement_progress_day",
                columnNames = {"member_achievement_id", "progress_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAchievementProgressDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_achievement_id", nullable = false)
    private MemberAchievement memberAchievement;

    @Column(name = "progress_date", nullable = false)
    private LocalDate progressDate;

    @Builder
    private MemberAchievementProgressDay(MemberAchievement memberAchievement, LocalDate progressDate) {
        this.memberAchievement = memberAchievement;
        this.progressDate = progressDate;
    }
}
