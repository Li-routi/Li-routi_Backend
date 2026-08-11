package com.lirouti.domain.achievement.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link com.lirouti.domain.achievement.event.AchievementProgressEvent} 처리 이력.
 *
 * <p>같은 사건(예: 루틴 체크 로그 1건)에 대한 이벤트가 API 재시도 등으로 중복 발행돼도,
 * {@code (sourceType, sourceId, conditionKey, memberId)} unique 제약 덕분에 두 번째
 * insert 는 제약 위반으로 실패한다. {@code AchievementProgressService} 는 이 실패를
 * "이미 반영됨" 신호로 보고 진행도 갱신을 건너뛴다.
 *
 * <p>{@code memberId} 를 키에 포함하는 이유: sourceId 가 항상 한 회원에게만 속하는 것은
 * 아니다 — 예를 들어 "좋아요" 이벤트의 sourceId 는 좋아요가 눌린 게시물 id인데, 이는 여러
 * 회원이 공유하는 값이다. memberId 없이 (sourceType, sourceId, conditionKey) 만으로
 * unique 를 걸면 회원 A의 좋아요를 기록한 뒤 회원 B가 같은 게시물에 좋아요를 눌러도
 * "이미 처리됨"으로 오판해 B의 진행도가 누락된다.
 */
@Entity
@Getter
@Table(
        name = "achievement_progress_event_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_achievement_progress_event_log",
                columnNames = {"source_type", "source_id", "condition_key", "member_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AchievementProgressEventLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "condition_key", nullable = false, length = 30)
    private String conditionKey;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Builder
    private AchievementProgressEventLog(Long memberId, String conditionKey,
                                        String sourceType, Long sourceId) {
        this.memberId = memberId;
        this.conditionKey = conditionKey;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }
}
