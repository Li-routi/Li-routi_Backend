package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupListQueryRepository.AssignmentCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.GroupCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.GroupProfileImageProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.GroupScheduleCountProjection;
import com.lirouti.domain.group.repository.GroupListQueryRepository.MyGroupProjection;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("참여 그룹 목록 QueryDSL Repository 테스트")
class GroupListQueryRepositoryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    @Autowired
    private GroupListQueryRepository groupListQueryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("목록과 배치 집계가 ACTIVE·현재 가입 회차·활성 루틴 정책을 함께 적용한다")
    void findMyGroupsAndAggregates_AppliesExistingActiveAndRejoinPolicies() {
        Member requester = member("조회 회원");
        Member activeAuthor = member("활성 작성자");
        Member withdrawnAuthor = member("탈퇴 작성자");
        withdrawnAuthor.withdraw("withdrawn-" + UUID.randomUUID() + "@example.com", "withdrawn-id",
                TODAY.atStartOfDay());

        Group olderGroup = group("LIST001");
        Group newerGroup = group("LIST002");
        Group deletedGroup = group("LIST003");
        deletedGroup.delete();
        LocalDateTime olderGroupLastVerificationAt = TODAY.minusDays(1).atTime(20, 0);
        olderGroup.updateLastVerificationAt(olderGroupLastVerificationAt);

        GroupMember olderMembership = membership(requester, olderGroup, TODAY.atTime(10, 0));
        membership(requester, newerGroup, TODAY.atTime(14, 0));
        membership(requester, deletedGroup, TODAY.atTime(15, 0));
        membership(activeAuthor, olderGroup, TODAY.atTime(8, 0));
        membership(withdrawnAuthor, olderGroup, TODAY.atTime(8, 0));

        GroupRoutine completedRoutine = routine(olderGroup, "완료 루틴", DayOfWeek.MONDAY);
        GroupRoutine pendingRoutine = routine(olderGroup, "대기 루틴", DayOfWeek.TUESDAY);
        GroupRoutine previousRoundRoutine = routine(olderGroup, "이전 회차 루틴", DayOfWeek.WEDNESDAY);
        GroupRoutine inactiveRoutine = routine(olderGroup, "비활성 루틴", DayOfWeek.THURSDAY);
        inactiveRoutine.delete();

        GroupRoutineAssignment completed = assignment(
                completedRoutine, requester, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment pending = assignment(
                pendingRoutine, requester, GroupRoutineAssignmentStatus.PENDING);
        GroupRoutineAssignment previousRound = assignment(
                previousRoundRoutine, requester, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment activeAuthorAssignment = assignment(
                completedRoutine, activeAuthor, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment withdrawnAuthorAssignment = assignment(
                pendingRoutine, withdrawnAuthor, GroupRoutineAssignmentStatus.COMPLETED);
        entityManager.persist(GroupRoutineVerification.builder()
                .assignment(activeAuthorAssignment)
                .verifiedAt(TODAY.atTime(9, 30))
                .imageUrl("verifications/active.png")
                .build());
        entityManager.persist(GroupRoutineVerification.builder()
                .assignment(withdrawnAuthorAssignment)
                .verifiedAt(TODAY.atTime(9, 30))
                .imageUrl("verifications/withdrawn.png")
                .build());
        entityManager.flush();

        setCreatedAt(previousRound, TODAY.atTime(9, 0));
        setCreatedAt(completed, TODAY.atTime(11, 0));
        setCreatedAt(pending, TODAY.atTime(11, 0));
        setCreatedAt(activeAuthorAssignment, TODAY.atTime(11, 0));
        entityManager.clear();

        List<MyGroupProjection> groups = groupListQueryRepository
                .findActiveGroupsByMemberId(requester.getId());
        List<Long> groupIds = groups.stream().map(MyGroupProjection::groupId).toList();

        assertThat(groups).extracting(MyGroupProjection::groupId)
                .containsExactly(newerGroup.getId(), olderGroup.getId());
        assertThat(groups).filteredOn(group -> group.groupId().equals(olderGroup.getId()))
                .singleElement()
                .extracting(MyGroupProjection::lastVerificationAt)
                .isEqualTo(olderGroupLastVerificationAt);
        assertThat(groupListQueryRepository.countActiveMembersByGroupIds(groupIds))
                .containsExactlyInAnyOrder(
                        new GroupCountProjection(olderGroup.getId(), 2L),
                        new GroupCountProjection(newerGroup.getId(), 1L)
                );
        assertThat(groupListQueryRepository.countActiveRoutinesByGroupIds(groupIds))
                .containsExactly(new GroupCountProjection(olderGroup.getId(), 3L));
        assertThat(groupListQueryRepository.findTodayAssignmentCounts(
                requester.getId(), groupIds, TODAY))
                .containsExactly(new AssignmentCountProjection(olderGroup.getId(), 2L, 1L));
        assertThat(groupListQueryRepository.countTodayVerificationsByGroupIds(groupIds, TODAY))
                .containsExactly(new GroupCountProjection(olderGroup.getId(), 1L));
        assertThat(groupListQueryRepository.findMonthlyAssignmentCounts(
                requester.getId(), groupIds, TODAY.withDayOfMonth(1), TODAY))
                .containsExactly(new AssignmentCountProjection(olderGroup.getId(), 2L, 1L));
        assertThat(groupListQueryRepository.countActiveSchedulesByGroupIds(groupIds))
                .containsExactlyInAnyOrder(
                        new GroupScheduleCountProjection(olderGroup.getId(), DayOfWeek.MONDAY, 1L),
                        new GroupScheduleCountProjection(olderGroup.getId(), DayOfWeek.TUESDAY, 1L),
                        new GroupScheduleCountProjection(olderGroup.getId(), DayOfWeek.WEDNESDAY, 1L)
                );
    }

    @Test
    @DisplayName("같은 joinedAt이면 GroupMember ID 내림차순으로 정렬하고 LEFT·KICKED 관계는 제외한다")
    void findActiveGroups_SameJoinedAt_OrdersByMembershipIdAndExcludesInactiveMemberships() {
        Member requester = member("정렬 회원");
        LocalDateTime joinedAt = TODAY.atTime(9, 0);
        Group firstActiveGroup = group("ORDER01");
        Group secondActiveGroup = group("ORDER02");
        Group leftGroup = group("ORDER03");
        Group kickedGroup = group("ORDER04");

        GroupMember firstActive = membership(requester, firstActiveGroup, joinedAt);
        GroupMember secondActive = membership(requester, secondActiveGroup, joinedAt);
        GroupMember leftMembership = membership(requester, leftGroup, joinedAt);
        GroupMember kickedMembership = membership(requester, kickedGroup, joinedAt);
        leftMembership.leave();
        kickedMembership.kick();
        entityManager.flush();
        entityManager.clear();

        List<MyGroupProjection> result = groupListQueryRepository
                .findActiveGroupsByMemberId(requester.getId());

        assertThat(result).extracting(MyGroupProjection::groupId)
                .containsExactly(secondActiveGroup.getId(), firstActiveGroup.getId());
        assertThat(secondActive.getId()).isGreaterThan(firstActive.getId());
    }

    @Test
    @DisplayName("여러 그룹의 ACTIVE 구성원 프로필 키를 null 포함 가입순으로 한 번에 조회한다")
    void findActiveMemberProfileImageKeysByGroupIds_BatchesActiveMembersInJoinOrder() {
        Group firstGroup = group("PROF001");
        Group secondGroup = group("PROF002");
        LocalDateTime sameJoinedAt = TODAY.atTime(9, 0);

        Member leftMember = member("탈퇴 구성원", "profiles/left.png");
        Member secondGroupMember = member("두번째 그룹 구성원", "profiles/second.png");
        Member firstGroupFirstMember = member("첫번째 그룹 구성원", "profiles/first.png");
        Member firstGroupDefaultProfileMember = member("기본 프로필 구성원", null);
        Member withdrawnButActiveMember = member("탈퇴 계정 구성원", "profiles/withdrawn.png");
        Member kickedMember = member("강퇴 구성원", "profiles/kicked.png");

        GroupMember leftMembership = membership(leftMember, firstGroup, TODAY.atTime(7, 0));
        membership(secondGroupMember, secondGroup, TODAY.atTime(8, 0));
        GroupMember firstMembership = membership(firstGroupFirstMember, firstGroup, sameJoinedAt);
        GroupMember defaultProfileMembership = membership(
                firstGroupDefaultProfileMember, firstGroup, sameJoinedAt);
        membership(withdrawnButActiveMember, secondGroup, TODAY.atTime(10, 0));
        GroupMember kickedMembership = membership(kickedMember, firstGroup, TODAY.atTime(11, 0));
        leftMembership.leave();
        kickedMembership.kick();
        withdrawnButActiveMember.withdraw(
                "withdrawn-" + UUID.randomUUID() + "@example.com",
                "withdrawn-" + UUID.randomUUID(),
                TODAY.atTime(12, 0)
        );
        entityManager.flush();
        entityManager.clear();

        List<GroupProfileImageProjection> result = groupListQueryRepository
                .findActiveMemberProfileImageKeysByGroupIds(
                        List.of(firstGroup.getId(), secondGroup.getId()));

        assertThat(result).containsExactly(
                new GroupProfileImageProjection(secondGroup.getId(), "profiles/second.png"),
                new GroupProfileImageProjection(firstGroup.getId(), "profiles/first.png"),
                new GroupProfileImageProjection(firstGroup.getId(), null),
                new GroupProfileImageProjection(secondGroup.getId(), "profiles/withdrawn.png")
        );
        assertThat(defaultProfileMembership.getId()).isGreaterThan(firstMembership.getId());
    }

    @Test
    @DisplayName("비활성 루틴의 오늘·월간 Assignment와 인증은 모든 목록 집계에서 제외한다")
    void aggregates_InactiveRoutine_ExcludesAssignmentsCompletionsAndVerifications() {
        Member requester = member("비활성 루틴 회원");
        Group group = group("INACT01");
        membership(requester, group, TODAY.atTime(8, 0));
        GroupRoutine activeRoutine = routine(group, "활성 집계 루틴", DayOfWeek.MONDAY);
        GroupRoutine inactiveRoutine = routine(group, "비활성 집계 루틴", DayOfWeek.TUESDAY);
        inactiveRoutine.delete();
        GroupRoutineAssignment activeAssignment = assignment(
                activeRoutine, requester, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment inactiveAssignment = assignment(
                inactiveRoutine, requester, GroupRoutineAssignmentStatus.COMPLETED);
        verification(activeAssignment, "verifications/active-routine.png");
        verification(inactiveAssignment, "verifications/inactive-routine.png");
        entityManager.flush();
        setCreatedAt(activeAssignment, TODAY.atTime(10, 0));
        setCreatedAt(inactiveAssignment, TODAY.atTime(10, 0));
        entityManager.clear();

        List<Long> groupIds = List.of(group.getId());

        assertThat(groupListQueryRepository.findTodayAssignmentCounts(requester.getId(), groupIds, TODAY))
                .containsExactly(new AssignmentCountProjection(group.getId(), 1L, 1L));
        assertThat(groupListQueryRepository.findMonthlyAssignmentCounts(
                requester.getId(), groupIds, TODAY.withDayOfMonth(1), TODAY))
                .containsExactly(new AssignmentCountProjection(group.getId(), 1L, 1L));
        assertThat(groupListQueryRepository.countTodayVerificationsByGroupIds(groupIds, TODAY))
                .containsExactly(new GroupCountProjection(group.getId(), 1L));
    }

    @Test
    @DisplayName("실제 leave 후 rejoin한 가입 회차 이전의 Assignment와 인증은 제외한다")
    void aggregates_LeaveThenRejoin_ExcludesPreviousRoundAssignmentsAndVerifications() {
        Member requester = member("재가입 흐름 회원");
        Group group = group("REJOIN1");
        GroupMember membership = membership(requester, group, TODAY.atTime(8, 0));
        GroupRoutine previousRoutine = routine(group, "이전 회차 인증 루틴", DayOfWeek.MONDAY);
        GroupRoutine currentRoutine = routine(group, "현재 회차 인증 루틴", DayOfWeek.TUESDAY);
        GroupRoutineAssignment previousAssignment = assignment(
                previousRoutine, requester, GroupRoutineAssignmentStatus.COMPLETED);
        verification(previousAssignment, "verifications/previous-round.png");
        entityManager.flush();
        setCreatedAt(previousAssignment, TODAY.atTime(9, 0));

        membership.leave();
        membership.rejoin(TODAY.atTime(10, 0));
        GroupRoutineAssignment currentAssignment = assignment(
                currentRoutine, requester, GroupRoutineAssignmentStatus.COMPLETED);
        verification(currentAssignment, "verifications/current-round.png");
        entityManager.flush();
        setCreatedAt(currentAssignment, TODAY.atTime(11, 0));
        entityManager.clear();

        List<Long> groupIds = List.of(group.getId());

        assertThat(groupListQueryRepository.findTodayAssignmentCounts(requester.getId(), groupIds, TODAY))
                .containsExactly(new AssignmentCountProjection(group.getId(), 1L, 1L));
        assertThat(groupListQueryRepository.findMonthlyAssignmentCounts(
                requester.getId(), groupIds, TODAY.withDayOfMonth(1), TODAY))
                .containsExactly(new AssignmentCountProjection(group.getId(), 1L, 1L));
        assertThat(groupListQueryRepository.countTodayVerificationsByGroupIds(groupIds, TODAY))
                .containsExactly(new GroupCountProjection(group.getId(), 1L));
    }

    @Test
    @DisplayName("오늘 그룹 인증 수는 LEFT·KICKED 작성자의 인증을 제외한다")
    void countTodayVerifications_AuthorsLeftOrKicked_ExcludesTheirVerifications() {
        Member requester = member("인증 조회 회원");
        Member activeAuthor = member("활성 인증 작성자");
        Member leftAuthor = member("탈퇴 인증 작성자");
        Member kickedAuthor = member("강퇴 인증 작성자");
        Group group = group("VERIFY1");
        membership(requester, group, TODAY.atTime(8, 0));
        membership(activeAuthor, group, TODAY.atTime(8, 0));
        GroupMember leftMembership = membership(leftAuthor, group, TODAY.atTime(8, 0));
        GroupMember kickedMembership = membership(kickedAuthor, group, TODAY.atTime(8, 0));
        GroupRoutine routine = routine(group, "인증 작성자 루틴", DayOfWeek.MONDAY);
        GroupRoutineAssignment activeAssignment = assignment(
                routine, activeAuthor, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment leftAssignment = assignment(
                routine, leftAuthor, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment kickedAssignment = assignment(
                routine, kickedAuthor, GroupRoutineAssignmentStatus.COMPLETED);
        verification(activeAssignment, "verifications/active-author.png");
        verification(leftAssignment, "verifications/left-author.png");
        verification(kickedAssignment, "verifications/kicked-author.png");
        leftMembership.leave();
        kickedMembership.kick();
        entityManager.flush();
        setCreatedAt(activeAssignment, TODAY.atTime(10, 0));
        setCreatedAt(leftAssignment, TODAY.atTime(10, 0));
        setCreatedAt(kickedAssignment, TODAY.atTime(10, 0));
        entityManager.clear();

        assertThat(groupListQueryRepository.countTodayVerificationsByGroupIds(
                List.of(group.getId()), TODAY))
                .containsExactly(new GroupCountProjection(group.getId(), 1L));
    }

    private Group group(String inviteCode) {
        Group group = Group.builder().name("목록 그룹 " + inviteCode).inviteCode(inviteCode).build();
        entityManager.persist(group);
        return group;
    }

    private Member member(String nickname) {
        return member(nickname, null);
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

    private GroupMember membership(Member member, Group group, LocalDateTime joinedAt) {
        GroupMember membership = GroupMember.createActive(
                member, group, GroupMemberRole.MEMBER, joinedAt);
        entityManager.persist(membership);
        return membership;
    }

    private GroupRoutine routine(Group group, String title, DayOfWeek repeatDay) {
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name(title + " 카테고리")
                .active(true)
                .build();
        entityManager.persist(category);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description(title + " 설명")
                .build();
        routine.addSchedule(repeatDay, LocalTime.of(9, 0), LocalTime.of(10, 0));
        entityManager.persist(routine);
        return routine;
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

    private void verification(GroupRoutineAssignment assignment, String imageUrl) {
        entityManager.persist(GroupRoutineVerification.builder()
                .assignment(assignment)
                .verifiedAt(TODAY.atTime(9, 30))
                .imageUrl(imageUrl)
                .build());
    }

    private void setCreatedAt(GroupRoutineAssignment assignment, LocalDateTime createdAt) {
        entityManager.createNativeQuery("""
                        update group_routine_assignment
                        set created_at = :createdAt
                        where id = :assignmentId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("assignmentId", assignment.getId())
                .executeUpdate();
    }
}
