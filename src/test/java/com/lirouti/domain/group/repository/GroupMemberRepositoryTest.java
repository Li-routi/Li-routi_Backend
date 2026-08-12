package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("GroupMemberRepository 활성 구성원 조회 테스트")
class GroupMemberRepositoryTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("대상 그룹의 ACTIVE 구성원 중 활성 계정만 조회한다")
    void findAllByGroupIdAndStatus_ActiveOnly_ReturnsScopedMembers() {
        // given
        Group target = group("A000001");
        Group other = group("A000002");
        GroupMember owner = membership(member(), target, GroupMemberRole.OWNER);
        GroupMember active = membership(member(), target, GroupMemberRole.MEMBER);
        GroupMember left = membership(member(), target, GroupMemberRole.MEMBER);
        GroupMember kicked = membership(member(), target, GroupMemberRole.MEMBER);
        Member withdrawnMember = member();
        membership(withdrawnMember, target, GroupMemberRole.MEMBER);
        membership(member(), other, GroupMemberRole.MEMBER);
        left.leave();
        kicked.kick();
        withdrawnMember.withdraw(
                "withdrawn-group-member@example.com",
                "withdrawn-group-member-social-id",
                LocalDateTime.of(2026, 7, 30, 12, 0)
        );
        em.flush();
        em.clear();

        // when
        List<GroupMember> result = groupMemberRepository
                .findAllByGroupIdAndStatus(target.getId(), GroupMemberStatus.ACTIVE);

        // then
        assertThat(result)
                .extracting(GroupMember::getId)
                .containsExactlyInAnyOrder(owner.getId(), active.getId());
    }

    @Test
    @DisplayName("참여 수는 역할과 무관하게 ACTIVE 관계와 ACTIVE 그룹만 집계한다")
    void countByMemberIdAndStatusAndGroupStatus_ActiveOnly_CountsOwnerAndMember() {
        // given
        Member target = member();
        Group owned = group("C000001");
        Group joined = group("C000002");
        Group leftGroup = group("C000003");
        Group kickedGroup = group("C000004");
        Group deletedGroup = group("C000005");
        membership(target, owned, GroupMemberRole.OWNER);
        membership(target, joined, GroupMemberRole.MEMBER);
        GroupMember left = membership(target, leftGroup, GroupMemberRole.MEMBER);
        GroupMember kicked = membership(target, kickedGroup, GroupMemberRole.MEMBER);
        membership(target, deletedGroup, GroupMemberRole.MEMBER);
        left.leave();
        kicked.kick();
        deletedGroup.delete();
        em.flush();
        em.clear();

        // when
        long result = groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                target.getId(),
                GroupMemberStatus.ACTIVE,
                GroupStatus.ACTIVE
        );

        // then
        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("그룹원 수는 OWNER와 MEMBER를 포함하고 탈퇴 관계와 비활성 계정을 제외한다")
    void countActiveMembersByGroupId_ActiveAccounts_CountsOwnerAndMember() {
        // given
        Group target = group("M000001");
        membership(member(), target, GroupMemberRole.OWNER);
        membership(member(), target, GroupMemberRole.MEMBER);
        GroupMember left = membership(member(), target, GroupMemberRole.MEMBER);
        GroupMember kicked = membership(member(), target, GroupMemberRole.MEMBER);
        Member withdrawn = member();
        membership(withdrawn, target, GroupMemberRole.MEMBER);
        membership(member(), group("M000002"), GroupMemberRole.MEMBER);
        left.leave();
        kicked.kick();
        withdrawn.withdraw(
                "withdrawn-member-limit@example.com",
                "withdrawn-member-limit-social-id",
                LocalDateTime.of(2026, 8, 1, 0, 0)
        );
        em.flush();
        em.clear();

        // when
        long result = groupMemberRepository.countActiveMembersByGroupId(
                target.getId(),
                GroupMemberStatus.ACTIVE
        );

        // then
        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("Preview ACTIVE 회원 ID는 활성 계정만 joinedAt, id 순서로 조회한다")
    void findMemberIdsByGroupIdAndStatusOrderByJoinedAtAscIdAsc_ReturnsOnlyActiveAccountsInStableOrder() {
        // given
        Group target = group("P000001");
        LocalDateTime firstJoinedAt = LocalDateTime.of(2026, 8, 1, 9, 0);
        LocalDateTime secondJoinedAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        GroupMember first = membership(member(), target, GroupMemberRole.MEMBER, firstJoinedAt);
        GroupMember sameTimeSecond = membership(member(), target, GroupMemberRole.MEMBER, firstJoinedAt);
        GroupMember last = membership(member(), target, GroupMemberRole.MEMBER, secondJoinedAt);
        GroupMember left = membership(member(), target, GroupMemberRole.MEMBER, secondJoinedAt);
        GroupMember kicked = membership(member(), target, GroupMemberRole.MEMBER, secondJoinedAt);
        Member withdrawn = member();
        GroupMember withdrawnMembership = membership(
                withdrawn, target, GroupMemberRole.MEMBER, secondJoinedAt);
        left.leave();
        kicked.kick();
        withdrawn.withdraw(
                "withdrawn-preview-member@example.com",
                "withdrawn-preview-member-social-id",
                LocalDateTime.of(2026, 8, 1, 11, 0)
        );
        em.flush();
        em.clear();

        // when
        List<Long> result = groupMemberRepository.findMemberIdsByGroupIdAndStatusOrderByJoinedAtAscIdAsc(
                target.getId(), GroupMemberStatus.ACTIVE);

        // then
        assertThat(result).containsExactly(
                first.getMember().getId(), sameTimeSecond.getMember().getId(), last.getMember().getId());
        assertThat(result).doesNotContain(withdrawn.getId());
    }

    @Test
    @DisplayName("MISSED 활동 초기화 조회는 ACTIVE인 현재 가입 회차만 반환한다")
    void findAllActiveCurrentMembershipsByAssignmentIdsForUpdate_ExcludesLeftAndKicked() {
        Group group = group("S000001");
        Member activeMember = member();
        Member leftMember = member();
        Member kickedMember = member();
        GroupMember active = membership(activeMember, group, GroupMemberRole.MEMBER);
        GroupMember left = membership(leftMember, group, GroupMemberRole.MEMBER);
        GroupMember kicked = membership(kickedMember, group, GroupMemberRole.MEMBER);
        left.leave();
        kicked.kick();

        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .group(group).name("상태 조회 카테고리").active(true).build();
        em.persist(category);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group).category(category).title("상태 조회 루틴").description("설명").build();
        em.persist(routine);
        GroupRoutineAssignment activeAssignment = assignment(routine, activeMember);
        GroupRoutineAssignment leftAssignment = assignment(routine, leftMember);
        GroupRoutineAssignment kickedAssignment = assignment(routine, kickedMember);
        em.flush();
        em.clear();

        List<GroupMember> result = groupMemberRepository
                .findAllActiveCurrentMembershipsByAssignmentIdsForUpdate(List.of(
                        activeAssignment.getId(), leftAssignment.getId(), kickedAssignment.getId()
                ));

        assertThat(result).extracting(GroupMember::getId).containsExactly(active.getId());
    }

    private Group group(String inviteCode) {
        Group group = Group.builder().name("테스트 그룹").inviteCode(inviteCode).build();
        em.persist(group);
        return group;
    }

    private Member member() {
        int value = sequence.incrementAndGet();
        Member member = Member.builder()
                .email("group-member-" + value + "@example.com")
                .nickname("구성원" + value)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-member-social-" + value)
                .build();
        em.persist(member);
        return member;
    }

    private GroupMember membership(Member member, Group group, GroupMemberRole role) {
        return membership(member, group, role, null);
    }

    private GroupMember membership(
            Member member,
            Group group,
            GroupMemberRole role,
            LocalDateTime joinedAt
    ) {
        GroupMember membership = GroupMember.builder()
                .member(member)
                .group(group)
                .role(role)
                .joinedAt(joinedAt)
                .build();
        em.persist(membership);
        return membership;
    }

    private GroupRoutineAssignment assignment(GroupRoutine routine, Member member) {
        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .assignedDate(LocalDate.of(2026, 8, 7))
                .scheduledStartTime(LocalTime.of(9, 0))
                .scheduledEndTime(LocalTime.of(10, 0))
                .status(GroupRoutineAssignmentStatus.MISSED)
                .build();
        em.persist(assignment);
        return assignment;
    }
}
