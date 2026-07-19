package com.lirouti.domain.group.entity;

import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원과 그룹 사이의 참여 관계다.
 *
 * <p>단순한 다대다 연결이 아니라 그룹 내 권한, 가입 상태, 가입·탈퇴 시각을 함께 관리한다.
 * 회원은 여러 그룹에 참여할 수 있으므로 권한 검증은 항상 memberId와 groupId를 함께 사용한다.</p>
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
        }
)
public class GroupMember extends BaseEntity {
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
    private GroupMember(Member member, Group group, GroupMemberRole role) {
        this.member = member;
        this.group = group;
        this.role = role;
        this.status = GroupMemberStatus.ACTIVE;
        this.joinedAt = LocalDateTime.now();
        this.leftAt = null;
    }

    /**
     * 일반 탈퇴 처리다. 방장이 탈퇴하면 그룹에 OWNER가 없어질 수 있으므로,
     * 권한을 위임하거나 그룹을 삭제하기 전에는 상태를 변경하지 않는다.
     */
    public void leave() {
        if (role == GroupMemberRole.OWNER) {
            throw new GroupException(GroupErrorCode.OWNER_CANNOT_LEAVE);
        }
        this.status = GroupMemberStatus.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    /**
     * 강제 탈퇴도 행을 삭제하지 않고 상태와 시각을 기록해 이력을 보존한다.
     */
    public void kick() {
        this.status = GroupMemberStatus.KICKED;
        this.leftAt = LocalDateTime.now();
    }
}
