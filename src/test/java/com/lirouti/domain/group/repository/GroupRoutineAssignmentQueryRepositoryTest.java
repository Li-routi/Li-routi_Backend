package com.lirouti.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("GroupRoutineAssignment 오늘 조회 Repository 테스트")
class GroupRoutineAssignmentQueryRepositoryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 25);

    @Autowired
    private GroupRoutineAssignmentRepository assignmentRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("회원의 여러 활성 그룹에서 오늘 할당만 Projection으로 시작 시각 순 조회한다")
    void findTodayAssignments_MultipleActiveGroups_ReturnsOrderedProjections() {
        // given
        Member member = member("query-member");
        Member otherMember = member("query-other");
        RoutineFixture morning = routineFixture("아침", "QRY0001");
        RoutineFixture evening = routineFixture("저녁", "QRY0002");
        membership(member, morning.group());
        membership(member, evening.group());
        membership(otherMember, morning.group());

        GroupRoutineAssignment eveningAssignment = assignment(
                evening.routine(), member, TODAY,
                LocalTime.of(18, 0), LocalTime.of(19, 0),
                GroupRoutineAssignmentStatus.PENDING
        );
        GroupRoutineAssignment morningAssignment = assignment(
                morning.routine(), member, TODAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
        assignment(
                morning.routine(), otherMember, TODAY,
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                GroupRoutineAssignmentStatus.COMPLETED
        );
        assignment(
                morning.routine(), member, TODAY.plusDays(1),
                LocalTime.of(7, 0), LocalTime.of(8, 0),
                GroupRoutineAssignmentStatus.PENDING
        );
        em.flush();
        em.clear();

        // when
        List<TodayAssignmentProjection> result = assignmentRepository
                .findTodayAssignmentsByMemberId(member.getId(), TODAY);

        // then
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(TodayAssignmentProjection::assignmentId)
                .containsExactly(morningAssignment.getId(), eveningAssignment.getId());
        assertThat(result.get(0)).satisfies(projection -> {
            assertThat(projection.routineId()).isEqualTo(morning.routine().getId());
            assertThat(projection.groupId()).isEqualTo(morning.group().getId());
            assertThat(projection.groupName()).isEqualTo("아침 그룹");
            assertThat(projection.categoryId()).isEqualTo(morning.category().getId());
            assertThat(projection.categoryName()).isEqualTo("아침 카테고리");
            assertThat(projection.title()).isEqualTo("아침 루틴");
            assertThat(projection.description()).isEqualTo("아침 설명");
            assertThat(projection.assignedDate()).isEqualTo(TODAY);
            assertThat(projection.scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(projection.scheduledEndTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(projection.status()).isEqualTo(GroupRoutineAssignmentStatus.IN_PROGRESS);
        });
    }

    @Test
    @DisplayName("비활성 구성원과 삭제 그룹 및 구성원 관계가 없는 기존 할당은 제외한다")
    void findTodayAssignments_InactiveMembershipOrGroup_ExcludesAssignments() {
        // given
        Member member = member("query-filter");
        RoutineFixture leftFixture = routineFixture("탈퇴", "QRY0003");
        RoutineFixture deletedGroupFixture = routineFixture("삭제", "QRY0004");
        RoutineFixture noMembershipFixture = routineFixture("비구성", "QRY0005");

        GroupMember leftMembership = membership(member, leftFixture.group());
        leftMembership.leave();
        membership(member, deletedGroupFixture.group());
        deletedGroupFixture.group().delete();

        assignment(leftFixture.routine(), member, TODAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), GroupRoutineAssignmentStatus.PENDING);
        assignment(deletedGroupFixture.routine(), member, TODAY,
                LocalTime.of(10, 0), LocalTime.of(11, 0), GroupRoutineAssignmentStatus.PENDING);
        assignment(noMembershipFixture.routine(), member, TODAY,
                LocalTime.of(11, 0), LocalTime.of(12, 0), GroupRoutineAssignmentStatus.PENDING);
        em.flush();
        em.clear();

        // when
        List<TodayAssignmentProjection> result = assignmentRepository
                .findTodayAssignmentsByMemberId(member.getId(), TODAY);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("오늘 할당이 없으면 빈 목록을 반환한다")
    void findTodayAssignments_NoAssignments_ReturnsEmptyList() {
        Member member = member("query-empty");
        em.flush();
        em.clear();

        assertThat(assignmentRepository.findTodayAssignmentsByMemberId(member.getId(), TODAY))
                .isEmpty();
    }

    private Member member(String identifier) {
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .nickname(identifier)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId(identifier + "-social")
                .build();
        em.persist(member);
        return member;
    }

    private RoutineFixture routineFixture(String name, String inviteCode) {
        Group group = Group.builder()
                .name(name + " 그룹")
                .inviteCode(inviteCode)
                .build();
        RoutineCategory category = RoutineCategory.builder()
                .name(name + " 카테고리")
                .active(true)
                .build();
        em.persist(group);
        em.persist(category);

        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(name + " 루틴")
                .description(name + " 설명")
                .build();
        em.persist(routine);
        return new RoutineFixture(group, category, routine);
    }

    private GroupMember membership(Member member, Group group) {
        GroupMember membership = GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.MEMBER)
                .build();
        em.persist(membership);
        return membership;
    }

    private GroupRoutineAssignment assignment(
            GroupRoutine routine,
            Member member,
            LocalDate assignedDate,
            LocalTime startTime,
            LocalTime endTime,
            GroupRoutineAssignmentStatus status
    ) {
        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .assignedDate(assignedDate)
                .scheduledStartTime(startTime)
                .scheduledEndTime(endTime)
                .status(status)
                .build();
        em.persist(assignment);
        return assignment;
    }

    private record RoutineFixture(Group group, RoutineCategory category, GroupRoutine routine) {
    }
}
