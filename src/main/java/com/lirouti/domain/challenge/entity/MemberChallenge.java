package com.lirouti.domain.challenge.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
 * 회원의 챌린지 참여 상태.
 *
 * 이 엔티티는 #12(조회)에서 참여자 수·오늘 완료자 수 집계를 위해 정의했다.
 * 참여·이탈·연속 참여일 같은 쓰기/비즈니스 로직은 #13에서 이 클래스에 추가한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_challenge",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_challenge",
                        columnNames = {"member_id", "challenge_id"}
                )
        }
)
public class MemberChallenge extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    // 재참여할 때마다 1 증가. 인증·연속 참여일·오늘 완료 여부는 이 회차 기준으로만 판단한다.
    @Column(name = "participation_round", nullable = false)
    private Integer participationRound;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "last_verified_date")
    private LocalDate lastVerifiedDate;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    // 참여 중 여부. false는 "그만둠"이며 회원 탈퇴와는 무관하다.
    @Column(nullable = false)
    private Boolean active;

    @Builder
    private MemberChallenge(
            Member member,
            Challenge challenge,
            Integer participationRound,
            Integer currentStreak,
            LocalDate lastVerifiedDate,
            LocalDateTime joinedAt,
            Boolean active
    ) {
        this.member = member;
        this.challenge = challenge;
        this.participationRound = (participationRound != null) ? participationRound : 1;
        this.currentStreak = (currentStreak != null) ? currentStreak : 0;
        this.lastVerifiedDate = lastVerifiedDate;
        this.joinedAt = joinedAt;
        this.active = (active != null) ? active : true;
    }
}
