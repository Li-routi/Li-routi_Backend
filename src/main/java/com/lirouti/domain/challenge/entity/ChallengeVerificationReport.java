package com.lirouti.domain.challenge.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증 사진에 대한 신고. <b>신고자 본인의 피드에서만</b> 그 인증을 가리는 용도다.
 *
 * 신고해도 인증은 삭제되지 않고 다른 회원에게는 그대로 보인다(database-schema.md).
 * 신고 누적으로 전체에게 숨기는 처리는 이 범위에 없다 — 임계값 기반 자동 숨김은 #60에서 다룬다.
 *
 * 유니크 제약이 중복 신고를 막는다. "이미 신고했는지" 선조회 후 저장하는 방식은 동시 요청에서
 * 둘 다 통과하므로, 제약 위반을 잡아 409로 바꾸는 쪽을 사용한다(챌린지 인증과 같은 방식).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "challenge_verification_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_verification_report_verification_reporter",
                        columnNames = {"challenge_verification_id", "reporter_id"}
                )
        }
)
public class ChallengeVerificationReport extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_verification_id", nullable = false)
    private ChallengeVerification challengeVerification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    // 신고 사유. 화면에서 선택 없이 신고할 수 있어야 하므로 nullable이다(database-schema.md).
    @Column(length = 255)
    private String reason;

    @Builder
    private ChallengeVerificationReport(
            ChallengeVerification challengeVerification,
            Member reporter,
            String reason
    ) {
        this.challengeVerification = challengeVerification;
        this.reporter = reporter;
        this.reason = reason;
    }
}
