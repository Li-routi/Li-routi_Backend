package com.lirouti.domain.verification.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.verification.enums.ReportType;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증 사진에 대한 신고. 신고자 본인의 피드에서 즉시 가리고,
 * 임계값만큼 쌓이면 전체 회원에게 가린다(ChallengeVerification.hiddenAt).
 *
 * 신고해도 인증은 삭제되지 않는다(database-schema.md). 임계값에 닿기 전까지는 신고자에게만 빠진다.
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

    /**
     * 신고 사유 종류. <b>이 컬럼이 NULL 인 것은 사유 선택을 도입하기 전에 들어온 신고다.</b>
     *
     * <p>새 신고는 요청 DTO 가 필수로 막으므로 반드시 값이 들어간다. 컬럼을 {@code NOT NULL} 로
     * 두지 않은 것은 옛 행을 채울 방법이 마땅치 않아서다 — {@code ETC} 로 채우면 "사용자가
     * 기타를 고른 것" 과 "그때는 사유가 없던 것" 이 통계에서 섞인다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 20)
    private ReportType reportType;

    /**
     * 직접 입력한 사유. <b>{@link ReportType#ETC} 일 때만 채워진다.</b>
     *
     * <p>나머지 넷은 화면에 입력란이 없다. 컬럼이 nullable 인 것은 그 때문이기도 하고,
     * 사유 선택 도입 전 신고가 비어 있기 때문이기도 하다.
     *
     * <p>컬럼은 255자지만 <b>요청 검증은 100자</b>다. 화면이 100자라 그보다 긴 값을 받아 주면
     * 다른 경로로 들어온 글이 화면에서 잘려 보인다. 컬럼을 줄이는 마이그레이션은 얻는 것에
     * 비해 위험해 그대로 둔다.
     */
    @Column(length = 255)
    private String reason;

    @Builder
    private ChallengeVerificationReport(
            ChallengeVerification challengeVerification,
            Member reporter,
            ReportType reportType,
            String reason
    ) {
        this.challengeVerification = challengeVerification;
        this.reporter = reporter;
        this.reportType = reportType;
        this.reason = reason;
    }
}
