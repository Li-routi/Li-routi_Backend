package com.lirouti.domain.challenge.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
 * 챌린지 참여의 일자별 인증 이력. 한 참여 회차 안에서 하루에 한 건만 존재한다.
 *
 * 이 엔티티는 #12(조회)에서 오늘 완료자 수 집계를 위해 정의했다.
 * 인증 등록/덮어쓰기 같은 쓰기 로직은 #14에서 이 클래스에 추가한다.
 * 소프트 삭제는 쓰지 않는다(당일 재인증은 덮어쓰기로 처리).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "challenge_verification",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_verification_round_date",
                        columnNames = {"member_challenge_id", "participation_round", "verified_date"}
                )
        }
)
public class ChallengeVerification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_challenge_id", nullable = false)
    private MemberChallenge memberChallenge;

    // 인증 당시의 참여 회차 스냅샷. 현재 회차 인증만 조회할 때 member_challenge의 회차와 비교한다.
    @Column(name = "participation_round", nullable = false)
    private Integer participationRound;

    @Column(name = "verified_date", nullable = false)
    private LocalDate verifiedDate;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    // 인증 사진은 필수다.
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(length = 255)
    private String content;

    @Builder
    private ChallengeVerification(
            MemberChallenge memberChallenge,
            Integer participationRound,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt,
            String imageUrl,
            String content
    ) {
        this.memberChallenge = memberChallenge;
        this.participationRound = participationRound;
        this.verifiedDate = verifiedDate;
        this.verifiedAt = verifiedAt;
        this.imageUrl = imageUrl;
        this.content = content;
    }
}
