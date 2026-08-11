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
 * {@code (sourceType, sourceId, conditionKey)} unique 제약 덕분에 두 번째 insert 는
 * 제약 위반으로 실패한다. {@code AchievementProgressService} 는 이 실패를 "이미 반영됨"
 * 신호로 보고 진행도 갱신을 건너뛴다 — 조회 후 분기가 아니라 insert 실패를 신호로 쓰는
 * 이유는, 조회-확인-삽입 사이에 동시 요청이 끼어드는 레이스를 DB 제약으로 원천 차단하기
 * 위해서다.
 */
@Entity
@Getter
@Table(
        name = "achievement_progress_event_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_achievement_progress_event_log",
                columnNames = {"source_type", "source_id", "condition_key"}
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
