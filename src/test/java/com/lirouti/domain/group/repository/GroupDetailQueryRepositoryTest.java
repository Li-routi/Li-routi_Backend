package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository.GroupMemberDetailProjection;
import com.lirouti.domain.group.repository.GroupDetailQueryRepository.TodayMemberProgressProjection;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("그룹 상세 QueryDSL Repository 테스트")
class GroupDetailQueryRepositoryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 8);

    @Autowired
    private GroupDetailQueryRepository groupDetailQueryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("ACTIVE 구성원 정보와 오늘 완료/전체 진행도를 멤버별로 일괄 조회한다")
    void findGroupDetail_ActiveMembersAndTodayProgress_ReturnsProjections() {
        // given
        Group group = Group.builder().name("상세 그룹").inviteCode("DETAIL1").build();
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("상세 카테고리").active(true).build();
        Member completedMember = member("완료 회원", null);
        Member pendingMember = member("대기 회원", null);
        Member noAssignmentMember = member("미할당 회원", null);
        entityManager.persist(group);
        entityManager.persist(category);

        GroupMember completedMembership = membership(group, completedMember);
        completedMembership.updateStatusMessage("완료했습니다");
        completedMembership.increaseTotalPokeCount();
        completedMembership.increaseTotalPokeCount();
        membership(group, pendingMember);
        membership(group, noAssignmentMember);

        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("상세 루틴")
                .description("상세 조회용 루틴")
                .build();
        GroupRoutine secondRoutine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("상세 두 번째 루틴")
                .description("상세 조회용 두 번째 루틴")
                .build();
        entityManager.persist(routine);
        entityManager.persist(secondRoutine);
        assignment(routine, completedMember, GroupRoutineAssignmentStatus.COMPLETED);
        assignment(secondRoutine, completedMember, GroupRoutineAssignmentStatus.PENDING);
        assignment(routine, pendingMember, GroupRoutineAssignmentStatus.IN_PROGRESS);
        entityManager.flush();
        entityManager.clear();

        // when
        List<GroupMemberDetailProjection> members = groupDetailQueryRepository
                .findActiveMemberDetails(group.getId());
        List<TodayMemberProgressProjection> progresses = groupDetailQueryRepository
                .findTodayMemberProgress(group.getId(), TODAY);

        // then
        assertThat(members).hasSize(3);
        assertThat(members).anySatisfy(member -> {
            assertThat(member.memberId()).isEqualTo(completedMember.getId());
            assertThat(member.name()).isEqualTo("완료 회원");
            assertThat(member.statusMessage()).isEqualTo("완료했습니다");
            assertThat(member.totalPokeCount()).isEqualTo(2L);
        });
        assertThat(progresses).containsExactlyInAnyOrder(
                new TodayMemberProgressProjection(completedMember.getId(), 2L, 1L),
                new TodayMemberProgressProjection(pendingMember.getId(), 1L, 0L)
        );
    }

    @Test
    @DisplayName("같은 날 재가입하면 이전 가입 회차에 생성된 할당은 진행도에서 제외한다")
    void findTodayMemberProgress_RejoinedMember_ExcludesPreviousMembershipAssignments() {
        // given
        Group group = Group.builder().name("재가입 그룹").inviteCode("REJOIN1").build();
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("재가입 카테고리").active(true).build();
        Member member = member("재가입 회원", null);
        entityManager.persist(group);
        entityManager.persist(category);
        GroupMember membership = membership(group, member);

        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("재가입 루틴")
                .description("재가입 진행도 검증용 루틴")
                .build();
        GroupRoutine laterRoutine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("재가입 이후 루틴")
                .description("재가입 이후 진행도 검증용 루틴")
                .build();
        entityManager.persist(routine);
        entityManager.persist(laterRoutine);
        GroupRoutineAssignment previousAssignment = assignment(
                routine, member, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment currentAssignment = assignment(
                laterRoutine, member, GroupRoutineAssignmentStatus.PENDING);
        entityManager.flush();

        LocalDateTime previousMembershipAssignmentTime = TODAY.atTime(9, 0);
        LocalDateTime rejoinedAt = TODAY.atTime(12, 0);
        LocalDateTime currentMembershipAssignmentTime = TODAY.atTime(13, 0);
        entityManager.createNativeQuery("""
                        update group_routine_assignment
                        set created_at = :previousMembershipAssignmentTime
                        where id = :assignmentId
                        """)
                .setParameter("previousMembershipAssignmentTime", previousMembershipAssignmentTime)
                .setParameter("assignmentId", previousAssignment.getId())
                .executeUpdate();
        entityManager.createNativeQuery("""
                        update group_routine_assignment
                        set created_at = :currentMembershipAssignmentTime
                        where id = :assignmentId
                        """)
                .setParameter("currentMembershipAssignmentTime", currentMembershipAssignmentTime)
                .setParameter("assignmentId", currentAssignment.getId())
                .executeUpdate();
        // 현재 브랜치에는 재가입 Command가 없으므로, 재가입 완료 상태를 DB에 직접 재현한다.
        entityManager.createNativeQuery("""
                        update group_member
                        set status = 'ACTIVE', joined_at = :rejoinedAt, left_at = null
                        where id = :membershipId
                        """)
                .setParameter("rejoinedAt", rejoinedAt)
                .setParameter("membershipId", membership.getId())
                .executeUpdate();
        entityManager.clear();

        // when
        List<TodayMemberProgressProjection> progresses = groupDetailQueryRepository
                .findTodayMemberProgress(group.getId(), TODAY);

        // then
        assertThat(progresses).containsExactly(
                new TodayMemberProgressProjection(member.getId(), 1L, 0L)
        );
    }

    private Member member(String nickname, String profileImageKey) {
        String identifier = UUID.randomUUID().toString();
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .nickname(nickname)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId(identifier)
                .build();
        member.updateProfile(nickname, profileImageKey);
        entityManager.persist(member);
        return member;
    }

    private GroupMember membership(Group group, Member member) {
        GroupMember membership = GroupMember.builder()
                .group(group)
                .member(member)
                .role(GroupMemberRole.MEMBER)
                .build();
        entityManager.persist(membership);
        return membership;
    }

    private GroupRoutineAssignment assignment(
            GroupRoutine routine,
            Member member,
            GroupRoutineAssignmentStatus status
    ) {
        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .assignedDate(TODAY)
                .scheduledStartTime(LocalTime.of(9, 0))
                .scheduledEndTime(LocalTime.of(10, 0))
                .status(status)
                .build();
        entityManager.persist(assignment);
        return assignment;
    }
}
