package com.lirouti.domain.achievement.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * ACH-EG-003(일찍 일어난 새 → 노아) 전용. "기상 루틴"(마감이 오전 10시 이전인 루틴)만
 * 필터링한 연속 기록. member_routine_streak(전체 루틴 통합)과 달리 대상이 좁다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_morning_routine_streak",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_morning_routine_streak_member",
                columnNames = {"member_id"}
        )
)
public class MemberMorningRoutineStreak extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "last_streak_completed_date")
    private LocalDate lastStreakCompletedDate;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private MemberMorningRoutineStreak(Long memberId) {
        this.memberId = memberId;
        this.currentStreak = 0;
        this.lastStreakCompletedDate = null;
    }

    public void recordCompletion(LocalDate completedDate) {
        if (completedDate.equals(lastStreakCompletedDate)) {
            return;
        }
        currentStreak++;
        lastStreakCompletedDate = completedDate;
    }

    /** 예정된 기상 루틴이 있었는데 완료 못 한 날의 배치가 호출한다. */
    public void resetCurrentStreak() {
        currentStreak = 0;
        lastStreakCompletedDate = null;
    }
}
