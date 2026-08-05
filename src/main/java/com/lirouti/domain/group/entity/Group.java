package com.lirouti.domain.group.entity;

import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked;

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
    private Group(String name, String inviteCode) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.isLocked = false;
        this.status = GroupStatus.ACTIVE;
    }

    public void updateName(String name) {
        this.name = name;
    }

    /** 신규 참여를 차단하기 위해 그룹을 잠근다. */
    public void lock() {
        this.isLocked = true;
    }

    /** 신규 참여를 다시 허용하기 위해 그룹을 잠금 해제한다. */
    public void unlock() {
        this.isLocked = false;
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
