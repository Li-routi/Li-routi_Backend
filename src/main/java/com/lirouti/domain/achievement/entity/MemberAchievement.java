package com.lirouti.domain.achievement.entity;

import com.lirouti.domain.achievement.enums.MemberAchievementStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 회원별 업적 진행/달성 현황.
 *
 * <p>조건 충족만으로 보상을 주지 않는다 — {@code status=ACHIEVED} 는 "받을 수 있는 상태"일
 * 뿐이고, {@code CLAIMED} 로의 전이는 반드시 {@link com.lirouti.domain.achievement.service.AchievementClaimService}
 * 를 거쳐 지갑 지급이 실제로 성공한 뒤에만 일어난다.
 */
@Entity
@Getter
@Table(
        name = "member_achievement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_achievement",
                columnNames = {"member_id", "achievement_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAchievement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberAchievementStatus status;

    /** 단일 조건 업적만 사용. COMPOSITE 는 {@link MemberAchievementCondition} 을 본다. */
    @Column(name = "current_progress", nullable = false)
    private int currentProgress;

    @Column(name = "achieved_at")
    private LocalDateTime achievedAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @OneToMany(mappedBy = "memberAchievement", fetch = FetchType.LAZY)
    private List<MemberAchievementCondition> conditionProgresses = new ArrayList<>();

    @Builder
    private MemberAchievement(Member member, Achievement achievement) {
        this.member = member;
        this.achievement = achievement;
        this.status = MemberAchievementStatus.IN_PROGRESS;
        this.currentProgress = 0;
    }

    /**
     * 진행도를 올린다. 목표치에 도달하면 ACHIEVED 로 전이한다.
     *
     * <p>이미 ACHIEVED/CLAIMED 인데 또 이벤트가 들어오면 아무 것도 하지 않는다 — 달성 이후의
     * 초과 행동(예: 인증 51회째)이 상태를 되돌리거나 progress 를 계속 늘리게 두지 않는다.
     */
    public void increaseProgress(int amount, int targetCount) {
        if (this.status != MemberAchievementStatus.IN_PROGRESS) {
            return;
        }
        this.currentProgress = Math.min(this.currentProgress + amount, targetCount);
        if (this.currentProgress >= targetCount) {
            markAchieved();
        }
    }

    /** NONE 타입(단발 이벤트) 업적을 즉시 달성 처리한다. */
    public void achieveImmediately() {
        if (this.status != MemberAchievementStatus.IN_PROGRESS) {
            return;
        }
        markAchieved();
    }

    private void markAchieved() {
        this.status = MemberAchievementStatus.ACHIEVED;
        this.achievedAt = LocalDateTime.now();
    }

    /**
     * 보상 수령 확정. ACHIEVED 상태에서만 전이한다.
     *
     * <p>호출부({@code AchievementClaimService})가 지갑 지급이 실제로 성공한 뒤에만 불러야
     * 한다 — 이 메서드 자체는 그 순서를 강제하지 않는다.
     */
    public void claim() {
        if (this.status != MemberAchievementStatus.ACHIEVED) {
            return;
        }
        this.status = MemberAchievementStatus.CLAIMED;
        this.claimedAt = LocalDateTime.now();
    }
}
