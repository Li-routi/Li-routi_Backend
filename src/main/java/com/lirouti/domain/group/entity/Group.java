package com.lirouti.domain.group.entity;

import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 여러 회원이 함께 루틴을 수행하는 그룹이다.
 * 방장은 이 엔티티에 별도 필드로 저장하지 않는다. 그룹별 권한의 단일 기준은
 * {@link GroupMember}의 role이며, OWNER 여부도 해당 참여 관계를 통해 판단한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// group은 SQL 예약어이므로 실제 테이블명에는 사용하지 않는다.
@Table(name = "member_group")
public class Group extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "invite_code", nullable = false, unique = true, length = 7)
    private String inviteCode;

    @Column(name = "invite_code_expires_at", nullable = false)
    private LocalDateTime inviteCodeExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status;

    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE)
    private List<GroupMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE)
    private List<GroupRoutine> routines = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE)
    private List<GroupRoutineCategory> routineCategories = new ArrayList<>();

    @Builder
    private Group(String name, String inviteCode, LocalDateTime inviteCodeExpiresAt) {
        this.name = name;
        this.inviteCode = inviteCode;
        // 기존 생성 경로는 만료 시각을 전달하지 않았다. V10의 NULL 보정 정책과 같이 즉시 만료시킨다.
        this.inviteCodeExpiresAt = inviteCodeExpiresAt != null
                ? inviteCodeExpiresAt
                : LocalDateTime.now();
        this.status = GroupStatus.ACTIVE;
    }

    public void updateName(String name) {
        this.name = name;
    }

    // 기존 초대코드를 무효화하고 새 코드와 말소 시각을 저장
    public void issueInviteCode(String inviteCode, LocalDateTime expiresAt) {
        this.inviteCode = inviteCode;
        this.inviteCodeExpiresAt = expiresAt;
    }

    public void addMember(GroupMember member) {
        if (member != null && !members.contains(member)) {
            members.add(member);
        }
    }

    public void addRoutine(GroupRoutine routine) {
        if (routine != null && !routines.contains(routine)) {
            routines.add(routine);
        }
    }

    public void addRoutineCategory(GroupRoutineCategory category) {
        if (category != null && !routineCategories.contains(category)) {
            routineCategories.add(category);
        }
    }

    /**
     * 그룹 데이터와 구성원 이력을 보존하기 위해 물리 삭제 대신 상태를 변경한다.
     */
    public void delete() {
        this.status = GroupStatus.DELETED;
    }
}
