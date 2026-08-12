package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "group_achievement_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_achievement_progress",
                columnNames = {"group_id", "achievement_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupAchievementProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Column(name = "current_progress", nullable = false)
    private int currentProgress;

    /**
     * GROUP_DISTINCT_DAY_COUNT 전용. 이 날짜에 이미 한 번 세었으면 같은 날 다시 세지 않는다.
     * GROUP_CUMULATIVE_COUNT 는 이 필드를 쓰지 않는다 - 인증 1건마다 그냥 누적하면 되므로
     * 날짜 중복 방지 개념 자체가 필요 없다.
     */
    @Column(name = "last_counted_date")
    private LocalDate lastCountedDate;

    @Column(name = "achieved_at")
    private LocalDateTime achievedAt;

    @Builder
    private GroupAchievementProgress(Long groupId, Achievement achievement) {
        this.groupId = groupId;
        this.achievement = achievement;
        this.currentProgress = 0;
    }

    public boolean isAchieved() {
        return achievedAt != null;
    }

    /**
     * GROUP_CUMULATIVE_COUNT 용. 인증이 들어올 때마다 그냥 절대값을 맞춘다.
     */
    public boolean syncProgressAndCheckNewlyAchieved(int currentValue, int targetCount) {
        if (isAchieved()) {
            return false;
        }
        this.currentProgress = Math.min(currentValue, targetCount);
        if (this.currentProgress >= targetCount) {
            this.achievedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }

    /**
     * GROUP_DISTINCT_DAY_COUNT 용. 오늘 이미 센 적이 있으면(같은 날 여러 완료·재확인)
     * 아무것도 하지 않고 false 를 돌려준다 - 하루는 최대 한 번만 카운트된다.
     */
    public boolean syncTodayCountAndCheckNewlyAchieved(int currentValue, int targetCount, LocalDate today) {
        if (isAchieved() || today.equals(lastCountedDate)) {
            return false;
        }
        this.lastCountedDate = today;
        this.currentProgress = Math.min(currentValue, targetCount);
        if (this.currentProgress >= targetCount) {
            this.achievedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }
}
