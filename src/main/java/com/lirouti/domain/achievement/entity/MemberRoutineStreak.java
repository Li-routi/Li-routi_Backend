package com.lirouti.domain.achievement.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 회원 전체 기준 루틴 연속기록(스트릭). 개인 루틴이든 그룹 루틴이든 하루에 하나라도
 * 완료하면 이어진다 — {@link com.lirouti.domain.group.entity.GroupMember}의
 * currentStreak/longestStreak/lastStreakCompletedDate와 같은 패턴이지만, 그건 "한
 * 그룹 안에서의" 연속기록이라 회원 전체를 보는 이 목적으로는 쓸 수 없어 별도로 둔다.
 *
 * <p>'100일 완주'(ACH-SP-002), '100일의 태양'(ACH-EG-012)이 이 값을 읽는다. 두 업적이
 * 정책상 100일 달성 시 동시에 채워져야 하므로, 진행도 갱신 쪽에서 이 스트릭 값 하나를
 * 두 업적에 동일하게 반영해야 한다.
 *
 * <p>같은 날 여러 루틴을 완료해도 {@link #recordCompletion}이 lastStreakCompletedDate로
 * 중복을 막으므로 스트릭이 하루에 여러 번 증가하지 않는다. 결석으로 인한 리셋은 이
 * 엔티티가 스스로 판단하지 않는다 — GroupMember와 동일하게, 별도 배치 작업이 결석을
 * 감지해 {@link #resetCurrentStreak()}를 호출하는 것을 전제로 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_routine_streak",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_routine_streak_member",
                columnNames = {"member_id"}
        )
)
public class MemberRoutineStreak extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "last_streak_completed_date")
    private LocalDate lastStreakCompletedDate;

    /**
     * 그룹 루틴 인증과 개인 루틴 인증이 둘 다 이 한 행을 동시에 갱신할 수 있어
     * GroupRoutineAssignment와 같은 낙관적 락을 둔다.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private MemberRoutineStreak(Long memberId) {
        this.memberId = memberId;
        this.currentStreak = 0;
        this.longestStreak = 0;
        this.lastStreakCompletedDate = null;
    }

    /** 같은 날짜에는 한 번만 현재 스트릭을 증가시킨다. GroupMember.recordStreakCompletion과 동일 규칙. */
    public void recordCompletion(LocalDate completedDate) {
        if (completedDate == null) {
            throw new IllegalArgumentException("스트릭 완료 날짜는 필수입니다.");
        }
        if (completedDate.equals(lastStreakCompletedDate)) {
            return;
        }
        currentStreak++;
        longestStreak = Math.max(longestStreak, currentStreak);
        lastStreakCompletedDate = completedDate;
    }

    /** 결석 감지 배치가 호출한다. 역대 최장 기록은 보존하고 현재 스트릭만 초기화한다. */
    public void resetCurrentStreak() {
        currentStreak = 0;
        lastStreakCompletedDate = null;
    }
}
