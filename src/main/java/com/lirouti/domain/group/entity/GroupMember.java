package com.lirouti.domain.group.entity;

import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원과 그룹 사이의 참여 관계다.
 * 단순한 다대다 연결이 아니라 그룹 내 권한, 가입 상태, 가입·탈퇴 시각을 함께 관리한다.
 * 회원은 여러 그룹에 참여할 수 있으므로 권한 검증은 항상 memberId와 groupId를 함께 사용한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_member",
        uniqueConstraints = {
            // 탈퇴 이력을 행으로 보존하므로 동일 회원과 그룹의 참여 관계는 한 행만 유지한다.
            @UniqueConstraint(
                    name = "uk_group_member_member_group",
                    columnNames = {"member_id", "group_id"}
            )
        },
        indexes = {
            @Index(name = "idx_group_member_group_status", columnList = "group_id, status")
        }
)
public class GroupMember extends BaseEntity {
    /** 한 회원이 역할과 관계없이 동시에 참여할 수 있는 ACTIVE 그룹 수. */
    public static final int MAX_ACTIVE_GROUP_COUNT = 6;

    /** 한 그룹에 역할과 관계없이 동시에 참여할 수 있는 활성 회원 수. */
    public static final int MAX_ACTIVE_MEMBER_COUNT_PER_GROUP = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    /**
     * 신규 참여 관계는 항상 ACTIVE 상태로 시작한다.
     * 그룹 생성자는 이 빌더에 OWNER role을 전달해 그룹 생성 트랜잭션 안에서 함께 저장한다.
     */
    @Builder
    private GroupMember(
            Member member,
            Group group,
            GroupMemberRole role,
            LocalDateTime joinedAt
    ) {
        this.member = member;
        this.group = group;
        this.role = role;
        this.status = GroupMemberStatus.ACTIVE;
        this.joinedAt = joinedAt == null ? LocalDateTime.now() : joinedAt;
        this.leftAt = null;
    }

    /**
     * 탈퇴 이력을 유지한 채 동일 참여 관계를 다시 활성화한다.
     * 가입 Command가 한 번 확정한 기준 시각을 전달해 가입 시각과 당일 할당 판정의 기준을 일치시킨다.
     */
    public void rejoin(LocalDateTime joinedAt) {
        if (joinedAt == null) {
            throw new IllegalArgumentException("재가입 시각은 필수입니다.");
        }
        if (status != GroupMemberStatus.LEFT) {
            throw new IllegalStateException("탈퇴한 관계만 재가입할 수 있습니다.");
        }
        this.role = GroupMemberRole.MEMBER;
        this.status = GroupMemberStatus.ACTIVE;
        this.joinedAt = joinedAt;
        this.leftAt = null;
    }

    /**
     * 일반 탈퇴 처리다. 방장이 탈퇴하면 그룹에 OWNER가 없어질 수 있으므로,
     * 권한을 위임하거나 그룹을 삭제하기 전에는 상태를 변경하지 않는다.
     */
    public void leave() {
        validateNotOwner(GroupErrorCode.OWNER_CANNOT_LEAVE);
        this.status = GroupMemberStatus.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    /**
     * 강제 탈퇴도 행을 삭제하지 않고 상태와 시각을 기록해 이력을 보존한다.
     * 방장 대상인 유저가 강제 퇴장을 당하는 경우도 OWNER가 없어질 수 있으므로,
     * 방장 권한의 유저는 강제퇴장 시키지 않는다.
     */
    public void kick() {
        validateNotOwner(GroupErrorCode.OWNER_CANNOT_KICK);
        this.status = GroupMemberStatus.KICKED;
        this.leftAt = LocalDateTime.now();
    }

    /*
     * leave와 kick 대상 유저가
     * 방장 권한이 아닌지에 대해 검증하기 위한 공통 메소드
     */
    private void validateNotOwner(GroupErrorCode errorCode) {
        if (role == GroupMemberRole.OWNER) {
            throw new GroupException(errorCode);
        }
    }
}
