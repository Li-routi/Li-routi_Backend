package com.lirouti.domain.verification.entity;

import com.lirouti.domain.group.entity.Group;
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

/** 재인증으로 다시 확인해야 하는 그룹 루틴 인증의 회원별 marker다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_verification_reread",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_routine_verification_reread_group_member_verification",
                columnNames = {"group_id", "member_id", "verification_id"}
        )
)
public class GroupRoutineVerificationReread extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 인증 삭제와 독립적으로 정리되는 marker 대상 ID다. */
    @Column(name = "verification_id", nullable = false)
    private Long verificationId;

    @Builder
    private GroupRoutineVerificationReread(Group group, Member member, Long verificationId) {
        this.group = group;
        this.member = member;
        this.verificationId = verificationId;
    }
}
