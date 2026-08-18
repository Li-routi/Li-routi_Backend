package com.lirouti.domain.achievement.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** ACH-EG-013(파도의 도전 → 파도) 전용. 회원이 고른 개인 루틴 하나만의 연속 기록. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_wave_routine_streak",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_wave_routine_streak_member",
                columnNames = {"member_id"}
        )
)
public class MemberWaveRoutineStreak extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "member_routine_id", nullable = false)
    private Long memberRoutineId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "last_streak_completed_date")
    private LocalDate lastStreakCompletedDate;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private MemberWaveRoutineStreak(Long memberId, Long memberRoutineId) {
        this.memberId = memberId;
        this.memberRoutineId = memberRoutineId;
        this.currentStreak = 0;
        this.lastStreakCompletedDate = null;
    }

    /** 추적 루틴을 바꾸면 지금까지 쌓은 진행도는 리셋된다 — 다른 루틴의 기록을 이어받을 수 없다. */
    public void changeTrackedRoutine(Long newMemberRoutineId) {
        this.memberRoutineId = newMemberRoutineId;
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

    public void resetCurrentStreak() {
        currentStreak = 0;
        lastStreakCompletedDate = null;
    }
}
