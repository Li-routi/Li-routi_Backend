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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원별 그룹 루틴 인증 읽음 위치다.
 *
 * <p>인증 행 자체를 참조하지 않고 마지막으로 확인한 ID만 보관한다. 인증 삭제가 읽음 위치를
 * 막지 않으며, ID가 단조 증가하므로 삭제된 ID도 이후 미조회 범위를 판정하는 커서로 유효하다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_verification_read",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_routine_verification_read_group_member",
                columnNames = {"group_id", "member_id"}
        ),
        indexes = @Index(
                name = "idx_group_routine_verification_read_group_member_cursor",
                columnList = "group_id, member_id, last_read_verification_id"
        )
)
public class GroupRoutineVerificationRead extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** FK가 아닌 단조 증가 커서다. */
    @Column(name = "last_read_verification_id")
    private Long lastReadVerificationId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    private GroupRoutineVerificationRead(Group group, Member member) {
        this.group = group;
        this.member = member;
        this.lastReadVerificationId = null;
        this.readAt = null;
    }
}
