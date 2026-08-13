package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code GroupAchievementProgressEvent} 처리 이력. {@code AchievementProgressEventLog}(회원
 * 단위)와 같은 목적이지만 unique 키가 {@code (achievement_id, source_type, source_id)} 다 —
 * member_id 가 빠진 이유는, 여기서 source_id(인증 행 id)가 이미 한 그룹의 사건 1건을 고유하게
 * 가리켜서 여러 그룹이 같은 source_id 를 공유할 일이 없기 때문이다(LIKE_COUNT 의 sourceId가
 * 여러 회원에게 공유되는 것과 다른 상황).
 */
@Entity
@Getter
@Table(
        name = "group_achievement_progress_event_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_achievement_progress_event_log",
                columnNames = {"achievement_id", "source_type", "source_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupAchievementProgressEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "achievement_id", nullable = false)
    private Long achievementId;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Builder
    private GroupAchievementProgressEventLog(Long groupId, Long achievementId,
                                             String sourceType, Long sourceId) {
        this.groupId = groupId;
        this.achievementId = achievementId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }
}
