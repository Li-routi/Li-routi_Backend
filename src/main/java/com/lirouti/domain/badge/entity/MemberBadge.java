package com.lirouti.domain.badge.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원이 획득한 배지. 행이 있으면 보유, 없으면 미보유 — MemberCharacter와 같은 패턴.
 *
 * <p>유니크가 지급의 멱등을 보장한다. 같은 업적을 두 번 claim해도(재시도 등) 두 번째
 * insert는 조용히 무시된다.
 */
@Entity
@Getter
@Table(
        name = "member_badge",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_badge",
                columnNames = {"member_id", "badge_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberBadge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    @Builder
    private MemberBadge(Member member, Badge badge, LocalDateTime earnedAt) {
        this.member = member;
        this.badge = badge;
        this.earnedAt = earnedAt;
    }
}