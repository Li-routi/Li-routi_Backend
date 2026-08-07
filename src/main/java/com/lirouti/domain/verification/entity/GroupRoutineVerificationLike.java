package com.lirouti.domain.verification.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;

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

/** 그룹 루틴 인증 게시물에 대한 좋아요. 취소는 행을 물리 삭제한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_verification_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_routine_verification_like_verification_member",
                columnNames = {"group_routine_verification_id", "member_id"}
        )
)
public class GroupRoutineVerificationLike extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_routine_verification_id", nullable = false)
    private GroupRoutineVerification groupRoutineVerification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    private GroupRoutineVerificationLike(
            GroupRoutineVerification groupRoutineVerification,
            Member member
    ) {
        this.groupRoutineVerification = groupRoutineVerification;
        this.member = member;
    }
}
