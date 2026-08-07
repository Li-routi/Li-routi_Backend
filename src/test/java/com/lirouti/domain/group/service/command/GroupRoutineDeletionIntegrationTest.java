package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.service.query.GroupQueryService;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@DisplayName("그룹 루틴 삭제 통합 테스트")
class GroupRoutineDeletionIntegrationTest {
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private GroupQueryService groupQueryService;
    @Autowired
    private GroupRoutineAssignmentCommandService assignmentCommandService;
    @Autowired
    private GroupRoutineAssignmentRepository assignmentRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("OWNER 삭제는 모든 회원의 미확정 할당만 삭제하고 금일 목록에서 루틴을 제외한다")
    void deleteRoutine_Owner_DeletesMutableAssignmentsAndExcludesTodayQueries() {
        // given
        Group group = group();
        Member owner = member();
        Member pendingMember = member();
        Member completedMember = member();
        Member missedMember = member();
        membership(owner, group, GroupMemberRole.OWNER);
        membership(pendingMember, group, GroupMemberRole.MEMBER);
        membership(completedMember, group, GroupMemberRole.MEMBER);
        membership(missedMember, group, GroupMemberRole.MEMBER);
        GroupRoutineCategory category = category();
        GroupRoutine target = routine(group, category, "삭제 대상");
        target.addSchedule(TODAY.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0));
        GroupRoutine unaffected = routine(group, category, "영향 없는 루틴");

        GroupRoutineAssignment pending = assignment(
                target, owner, GroupRoutineAssignmentStatus.PENDING);
        GroupRoutineAssignment inProgress = assignment(
                target, pendingMember, GroupRoutineAssignmentStatus.IN_PROGRESS);
        GroupRoutineAssignment completed = assignment(
                target, completedMember, GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment missed = assignment(
                target, missedMember, GroupRoutineAssignmentStatus.MISSED);
        GroupRoutineAssignment unaffectedAssignment = assignment(
                unaffected, owner, GroupRoutineAssignmentStatus.PENDING);
        em.flush();

        Long groupId = group.getId();
        Long ownerId = owner.getId();
        Long targetId = target.getId();
        Long unaffectedId = unaffected.getId();
        Long pendingId = pending.getId();
        Long inProgressId = inProgress.getId();
        Long completedId = completed.getId();
        Long missedId = missed.getId();
        Long unaffectedAssignmentId = unaffectedAssignment.getId();
        List<Long> memberIds = List.of(
                ownerId, pendingMember.getId(), completedMember.getId(), missedMember.getId());
        em.clear();

        memberIds.forEach(memberId -> assertThat(todayRoutineIds(memberId)).contains(targetId));

        // when
        groupCommandService.deleteRoutine(groupId, targetId, ownerId);
        em.flush();
        em.clear();

        // then
        assertThat(em.find(GroupRoutine.class, targetId).getActive()).isFalse();
        assertThat(em.find(GroupRoutineAssignment.class, pendingId)).isNull();
        assertThat(em.find(GroupRoutineAssignment.class, inProgressId)).isNull();
        assertThat(em.find(GroupRoutineAssignment.class, completedId))
                .extracting(GroupRoutineAssignment::getStatus)
                .isEqualTo(GroupRoutineAssignmentStatus.COMPLETED);
        assertThat(em.find(GroupRoutineAssignment.class, missedId))
                .extracting(GroupRoutineAssignment::getStatus)
                .isEqualTo(GroupRoutineAssignmentStatus.MISSED);
        assertThat(em.find(GroupRoutineAssignment.class, unaffectedAssignmentId)).isNotNull();

        memberIds.forEach(memberId -> assertThat(todayRoutineIds(memberId)).doesNotContain(targetId));
        assertThat(todayRoutineIds(ownerId)).containsExactly(unaffectedId);
        assertThat(assignmentCommandService.assignScheduledRoutinesForDate(TODAY)).isZero();
    }

    @Test
    @DisplayName("일반 구성원과 비활성 구성원 및 비구성원은 그룹 루틴을 삭제할 수 없다")
    void deleteRoutine_MemberInactiveMemberOrOutsider_ThrowsAccessDenied() {
        // given
        Group group = group();
        Member owner = member();
        Member regularMember = member();
        Member inactiveMember = member();
        Member outsider = member();
        membership(owner, group, GroupMemberRole.OWNER);
        membership(regularMember, group, GroupMemberRole.MEMBER);
        GroupMember inactiveMembership = membership(
                inactiveMember, group, GroupMemberRole.MEMBER);
        inactiveMembership.leave();
        GroupRoutine routine = routine(group, category(), "권한 대상");
        em.flush();
        em.clear();

        // when & then
        assertGroupError(
                () -> groupCommandService.deleteRoutine(
                        group.getId(), routine.getId(), regularMember.getId()),
                GroupErrorCode.GROUP_OWNER_ACCESS_DENIED
        );
        assertGroupError(
                () -> groupCommandService.deleteRoutine(
                        group.getId(), routine.getId(), outsider.getId()),
                GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED
        );
        assertGroupError(
                () -> groupCommandService.deleteRoutine(
                        group.getId(), routine.getId(), inactiveMember.getId()),
                GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED
        );
    }

    @Test
    @DisplayName("다른 그룹 OWNER와 요청 그룹에 속하지 않은 루틴을 차단한다")
    void deleteRoutine_OtherGroupOwnerOrRoutine_RejectsRequest() {
        // given
        Group targetGroup = group();
        Group otherGroup = group();
        Member targetOwner = member();
        Member otherOwner = member();
        membership(targetOwner, targetGroup, GroupMemberRole.OWNER);
        membership(otherOwner, otherGroup, GroupMemberRole.OWNER);
        GroupRoutine targetRoutine = routine(targetGroup, category(), "대상 그룹 루틴");
        GroupRoutine otherRoutine = routine(otherGroup, category(), "다른 그룹 루틴");
        em.flush();
        em.clear();

        // when & then
        assertGroupError(
                () -> groupCommandService.deleteRoutine(
                        targetGroup.getId(), targetRoutine.getId(), otherOwner.getId()),
                GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED
        );
        assertGroupError(
                () -> groupCommandService.deleteRoutine(
                        targetGroup.getId(), otherRoutine.getId(), targetOwner.getId()),
                GroupErrorCode.GROUP_ROUTINE_NOT_FOUND
        );
    }

    @Test
    @DisplayName("존재하지 않거나 이미 삭제된 루틴은 같은 찾을 수 없음 예외를 반환한다")
    void deleteRoutine_MissingOrInactiveRoutine_ThrowsNotFound() {
        // given
        Group group = group();
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        GroupRoutine routine = routine(group, category(), "반복 삭제 대상");
        em.flush();
        Long groupId = group.getId();
        Long ownerId = owner.getId();
        Long routineId = routine.getId();
        em.clear();

        groupCommandService.deleteRoutine(groupId, routineId, ownerId);
        em.flush();
        em.clear();

        // when & then
        assertGroupError(
                () -> groupCommandService.deleteRoutine(groupId, routineId, ownerId),
                GroupErrorCode.GROUP_ROUTINE_NOT_FOUND
        );
        assertGroupError(
                () -> groupCommandService.deleteRoutine(groupId, Long.MAX_VALUE, ownerId),
                GroupErrorCode.GROUP_ROUTINE_NOT_FOUND
        );
        assertGroupError(
                () -> groupCommandService.deleteRoutine(Long.MAX_VALUE, routineId, ownerId),
                GroupErrorCode.GROUP_NOT_FOUND
        );
    }

    private List<Long> todayRoutineIds(Long memberId) {
        GroupResDTO.TodayRoutineList result = groupQueryService.getTodayRoutines(memberId);
        return result.routines().stream().map(GroupResDTO.TodayRoutine::routineId).toList();
    }

    private void assertGroupError(Runnable action, GroupErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(errorCode);
    }

    private Group group() {
        int value = sequence.incrementAndGet();
        Group group = Group.builder()
                .name("삭제 그룹 " + value)
                .inviteCode(String.format("D%06d", value))
                .build();
        em.persist(group);
        return group;
    }

    private Member member() {
        int value = sequence.incrementAndGet();
        Member member = Member.builder()
                .email("delete-member-" + value + "@example.com")
                .nickname("삭제회원" + value)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("delete-member-social-" + value)
                .build();
        em.persist(member);
        return member;
    }

    private GroupMember membership(Member member, Group group, GroupMemberRole role) {
        GroupMember groupMember = GroupMember.builder()
                .member(member)
                .group(group)
                .role(role)
                .build();
        em.persist(groupMember);
        return groupMember;
    }

    private GroupRoutineCategory category() {
        int value = sequence.incrementAndGet();
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("삭제 카테고리 " + value)
                .active(true)
                .build();
        em.persist(category);
        return category;
    }

    private GroupRoutine routine(
            Group group,
            GroupRoutineCategory category,
            String title
    ) {
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description("삭제 통합 테스트")
                .build();
        em.persist(routine);
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
        assignmentRepository.save(assignment);
        return assignment;
    }
}
