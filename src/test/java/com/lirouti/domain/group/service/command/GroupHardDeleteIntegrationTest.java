package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.GroupRoutineScheduleRepository;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@DisplayName("그룹 Hard Delete cascade 통합 테스트")
class GroupHardDeleteIntegrationTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private GroupQueryService groupQueryService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Clock clock;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private GroupRoutineCategoryRepository groupRoutineCategoryRepository;
    @Autowired
    private GroupRoutineRepository groupRoutineRepository;
    @Autowired
    private GroupRoutineScheduleRepository groupRoutineScheduleRepository;
    @Autowired
    private GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    @Autowired
    private GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    @Autowired
    private RoutineCategoryRepository routineCategoryRepository;
    @Autowired
    private MemberRoutineRepository memberRoutineRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("그룹 삭제는 종속 데이터만 Hard Delete하고 다른 그룹·회원·공용·개인 데이터를 보존한다")
    void deleteGroup_CascadeDeletesTargetAndPreservesUnrelatedData() {
        // given
        Group targetGroup = group("HDT001");
        Group otherGroup = group("HDO001");
        Member owner = member();
        Member member = member();

        GroupMember targetOwnerMembership = membership(targetGroup, owner, GroupMemberRole.OWNER);
        GroupMember targetMemberMembership = membership(targetGroup, member, GroupMemberRole.MEMBER);
        GroupMember otherOwnerMembership = membership(otherGroup, owner, GroupMemberRole.OWNER);
        GroupMember otherMemberMembership = membership(otherGroup, member, GroupMemberRole.MEMBER);

        GroupRoutineCategory targetCategory = groupCategory(targetGroup, "삭제 대상 카테고리");
        GroupRoutineCategory otherCategory = groupCategory(otherGroup, "보존 카테고리");
        GroupRoutineCategory fixedCategory = fixedCategory("보존 공용 카테고리");
        GroupRoutine targetRoutine = routine(targetGroup, targetCategory, "삭제 대상 루틴");
        GroupRoutine fixedCategoryRoutine = routine(targetGroup, fixedCategory, "공용 카테고리 대상 루틴");
        GroupRoutine otherRoutine = routine(otherGroup, otherCategory, "보존 루틴");
        targetRoutine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        targetRoutine.addSchedule(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(12, 0));
        otherRoutine.addSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        entityManager.persist(targetRoutine);
        entityManager.persist(fixedCategoryRoutine);
        entityManager.persist(otherRoutine);
        targetGroup.addRoutine(targetRoutine);
        targetGroup.addRoutineCategory(targetCategory);
        otherGroup.addRoutine(otherRoutine);
        otherGroup.addRoutineCategory(otherCategory);

        GroupRoutineAssignment pendingAssignment = assignment(
                targetRoutine, owner, GroupRoutineAssignmentStatus.PENDING, LocalDate.now(clock));
        GroupRoutineAssignment completedAssignment = assignment(
                targetRoutine, member, GroupRoutineAssignmentStatus.COMPLETED, LocalDate.now(clock));
        GroupRoutineAssignment otherAssignment = assignment(
                otherRoutine, member, GroupRoutineAssignmentStatus.IN_PROGRESS, LocalDate.now(clock));
        entityManager.persist(pendingAssignment);
        entityManager.persist(completedAssignment);
        entityManager.persist(otherAssignment);
        targetRoutine.addAssignment(pendingAssignment);
        targetRoutine.addAssignment(completedAssignment);
        otherRoutine.addAssignment(otherAssignment);

        GroupRoutineVerification targetVerification = verification(completedAssignment);
        GroupRoutineVerification otherVerification = verification(otherAssignment);
        entityManager.persist(targetVerification);
        entityManager.persist(otherVerification);
        completedAssignment.attachVerification(targetVerification);
        otherAssignment.attachVerification(otherVerification);

        RoutineCategory personalCategory = personalCategory(owner);
        MemberRoutine personalRoutine = MemberRoutine.builder()
                .member(owner)
                .category(personalCategory)
                .name("보존 개인 루틴")
                .endTime(LocalTime.of(23, 0))
                .active(true)
                .build();
        personalRoutine.addSchedule(DayOfWeek.THURSDAY);
        routineCategoryRepository.save(personalCategory);
        memberRoutineRepository.save(personalRoutine);

        entityManager.flush();
        CascadeIds ids = CascadeIds.from(
                targetGroup,
                otherGroup,
                targetOwnerMembership,
                targetMemberMembership,
                otherOwnerMembership,
                otherMemberMembership,
                targetCategory,
                otherCategory,
                targetRoutine,
                otherRoutine,
                pendingAssignment,
                completedAssignment,
                otherAssignment,
                targetVerification,
                otherVerification,
                personalCategory,
                personalRoutine
        );
        entityManager.clear();

        // when
        groupCommandService.deleteGroup(ids.targetGroupId(), owner.getId());
        entityManager.flush();
        entityManager.clear();

        // then: 삭제 대상 Group과 전체 cascade 경로
        assertThat(groupRepository.existsById(ids.targetGroupId())).isFalse();
        assertThat(groupMemberRepository.existsById(ids.targetOwnerMembershipId())).isFalse();
        assertThat(groupMemberRepository.existsById(ids.targetMemberMembershipId())).isFalse();
        assertThat(groupRoutineCategoryRepository.existsById(ids.targetCategoryId())).isFalse();
        assertThat(groupRoutineRepository.existsById(ids.targetRoutineId())).isFalse();
        assertThat(groupRoutineRepository.existsById(fixedCategoryRoutine.getId())).isFalse();
        ids.targetScheduleIds().forEach(id ->
                assertThat(groupRoutineScheduleRepository.existsById(id)).isFalse());
        assertThat(groupRoutineAssignmentRepository.existsById(ids.pendingAssignmentId())).isFalse();
        assertThat(groupRoutineAssignmentRepository.existsById(ids.completedAssignmentId())).isFalse();
        assertThat(groupRoutineVerificationRepository.existsById(ids.targetVerificationId())).isFalse();

        // then: 다른 그룹과 그 하위 데이터
        assertThat(groupRepository.existsById(ids.otherGroupId())).isTrue();
        assertThat(groupMemberRepository.existsById(ids.otherOwnerMembershipId())).isTrue();
        assertThat(groupMemberRepository.existsById(ids.otherMemberMembershipId())).isTrue();
        assertThat(groupRoutineCategoryRepository.existsById(ids.otherCategoryId())).isTrue();
        assertThat(groupRoutineRepository.existsById(ids.otherRoutineId())).isTrue();
        ids.otherScheduleIds().forEach(id ->
                assertThat(groupRoutineScheduleRepository.existsById(id)).isTrue());
        assertThat(groupRoutineAssignmentRepository.existsById(ids.otherAssignmentId())).isTrue();
        assertThat(groupRoutineVerificationRepository.existsById(ids.otherVerificationId())).isTrue();

        // then: Member 계정, 공용 기본 그룹 카테고리, 개인 루틴과 개인 카테고리
        assertThat(entityManager.find(Member.class, ids.ownerId())).isNotNull();
        assertThat(entityManager.find(Member.class, ids.memberId())).isNotNull();
        assertThat(groupRoutineCategoryRepository.existsById(fixedCategory.getId())).isTrue();
        assertThat(routineCategoryRepository.existsById(ids.personalCategoryId())).isTrue();
        assertThat(memberRoutineRepository.existsById(ids.personalRoutineId())).isTrue();
        Long personalScheduleCount = entityManager.createQuery(
                        "select count(schedule) from MemberRoutineSchedule schedule "
                                + "where schedule.memberRoutine.id = :routineId",
                        Long.class
                )
                .setParameter("routineId", ids.personalRoutineId())
                .getSingleResult();
        assertThat(personalScheduleCount).isEqualTo(1L);

        GroupResDTO.TodayRoutineList todayRoutines = groupQueryService
                .getTodayRoutines(ids.memberId());
        assertThat(todayRoutines.routines())
                .singleElement()
                .extracting(GroupResDTO.TodayRoutine::groupId)
                .isEqualTo(ids.otherGroupId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("삭제 트랜잭션이 롤백되면 그룹과 종속 데이터가 모두 유지된다")
    void deleteGroup_WhenTransactionRollsBack_PreservesGroupAndChildren() {
        RollbackIds ids = new TransactionTemplate(transactionManager).execute(status -> {
            Group group = group("HDR" + sequence.incrementAndGet());
            Member owner = member();
            GroupMember membership = membership(group, owner, GroupMemberRole.OWNER);
            GroupRoutineCategory category = groupCategory(group, "롤백 카테고리");
            GroupRoutine routine = routine(group, category, "롤백 루틴");
            routine.addSchedule(DayOfWeek.FRIDAY, LocalTime.of(15, 0), LocalTime.of(16, 0));
            entityManager.persist(routine);
            GroupRoutineAssignment assignment = assignment(
                    routine, owner, GroupRoutineAssignmentStatus.PENDING, LocalDate.now(clock));
            entityManager.persist(assignment);
            routine.addAssignment(assignment);
            GroupRoutineVerification verification = verification(assignment);
            entityManager.persist(verification);
            assignment.attachVerification(verification);
            entityManager.flush();
            return new RollbackIds(
                    group.getId(),
                    owner.getId(),
                    membership.getId(),
                    category.getId(),
                    routine.getId(),
                    routine.getSchedules().get(0).getId(),
                    assignment.getId(),
                    verification.getId()
            );
        });

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    groupCommandService.deleteGroup(ids.groupId(), ids.ownerId());
                    throw new IllegalStateException("rollback verification");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback verification");

        try {
            entityManager.clear();
            assertThat(groupRepository.existsById(ids.groupId())).isTrue();
            assertThat(groupMemberRepository.existsById(ids.membershipId())).isTrue();
            assertThat(groupRoutineCategoryRepository.existsById(ids.categoryId())).isTrue();
            assertThat(groupRoutineRepository.existsById(ids.routineId())).isTrue();
            assertThat(groupRoutineScheduleRepository.existsById(ids.scheduleId())).isTrue();
            assertThat(groupRoutineAssignmentRepository.existsById(ids.assignmentId())).isTrue();
            assertThat(groupRoutineVerificationRepository.existsById(ids.verificationId())).isTrue();
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    groupCommandService.deleteGroup(ids.groupId(), ids.ownerId()));
        }
    }

    private Group group(String inviteCode) {
        int number = sequence.incrementAndGet();
        Group group = Group.builder()
                .name("Hard Delete 그룹 " + number)
                .inviteCode(inviteCode)
                .build();
        entityManager.persist(group);
        return group;
    }

    private Member member() {
        int number = sequence.incrementAndGet();
        String uniqueId = UUID.randomUUID().toString();
        Member member = Member.builder()
                .email("hard-delete-" + uniqueId + "@example.com")
                .nickname("Hard Delete 회원 " + number)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("hard-delete-social-" + uniqueId)
                .build();
        entityManager.persist(member);
        return member;
    }

    private GroupMember membership(Group group, Member member, GroupMemberRole role) {
        GroupMember membership = GroupMember.builder()
                .group(group)
                .member(member)
                .role(role)
                .build();
        entityManager.persist(membership);
        group.addMember(membership);
        return membership;
    }

    private GroupRoutineCategory groupCategory(Group group, String name) {
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .group(group)
                .name(name)
                .active(true)
                .build();
        entityManager.persist(category);
        group.addRoutineCategory(category);
        return category;
    }

    private GroupRoutineCategory fixedCategory(String name) {
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name(name)
                .active(true)
                .build();
        entityManager.persist(category);
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
                .description("Hard Delete 통합 테스트 루틴")
                .build();
        group.addRoutine(routine);
        category.addRoutine(routine);
        return routine;
    }

    private GroupRoutineAssignment assignment(
            GroupRoutine routine,
            Member member,
            GroupRoutineAssignmentStatus status,
            LocalDate assignedDate
    ) {
        return GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .assignedDate(assignedDate)
                .scheduledStartTime(LocalTime.of(9, 0))
                .scheduledEndTime(LocalTime.of(10, 0))
                .status(status)
                .build();
    }

    private GroupRoutineVerification verification(GroupRoutineAssignment assignment) {
        return GroupRoutineVerification.builder()
                .assignment(assignment)
                .verifiedAt(LocalDateTime.now())
                .imageUrl("group-routine-verifications/hard-delete-test.jpg")
                .content("Hard Delete 통합 테스트 인증")
                .build();
    }

    private RoutineCategory personalCategory(Member owner) {
        return RoutineCategory.builder()
                .owner(owner)
                .name("개인 보존 카테고리 " + sequence.incrementAndGet())
                .active(true)
                .build();
    }

    private record CascadeIds(
            Long targetGroupId,
            Long otherGroupId,
            Long ownerId,
            Long memberId,
            Long targetOwnerMembershipId,
            Long targetMemberMembershipId,
            Long otherOwnerMembershipId,
            Long otherMemberMembershipId,
            Long targetCategoryId,
            Long otherCategoryId,
            Long targetRoutineId,
            Long otherRoutineId,
            List<Long> targetScheduleIds,
            List<Long> otherScheduleIds,
            Long pendingAssignmentId,
            Long completedAssignmentId,
            Long otherAssignmentId,
            Long targetVerificationId,
            Long otherVerificationId,
            Long personalCategoryId,
            Long personalRoutineId
    ) {
        private static CascadeIds from(
                Group targetGroup,
                Group otherGroup,
                GroupMember targetOwnerMembership,
                GroupMember targetMemberMembership,
                GroupMember otherOwnerMembership,
                GroupMember otherMemberMembership,
                GroupRoutineCategory targetCategory,
                GroupRoutineCategory otherCategory,
                GroupRoutine targetRoutine,
                GroupRoutine otherRoutine,
                GroupRoutineAssignment pendingAssignment,
                GroupRoutineAssignment completedAssignment,
                GroupRoutineAssignment otherAssignment,
                GroupRoutineVerification targetVerification,
                GroupRoutineVerification otherVerification,
                RoutineCategory personalCategory,
                MemberRoutine personalRoutine
        ) {
            return new CascadeIds(
                    targetGroup.getId(),
                    otherGroup.getId(),
                    targetOwnerMembership.getMember().getId(),
                    targetMemberMembership.getMember().getId(),
                    targetOwnerMembership.getId(),
                    targetMemberMembership.getId(),
                    otherOwnerMembership.getId(),
                    otherMemberMembership.getId(),
                    targetCategory.getId(),
                    otherCategory.getId(),
                    targetRoutine.getId(),
                    otherRoutine.getId(),
                    targetRoutine.getSchedules().stream()
                            .map(schedule -> schedule.getId())
                            .toList(),
                    otherRoutine.getSchedules().stream()
                            .map(schedule -> schedule.getId())
                            .toList(),
                    pendingAssignment.getId(),
                    completedAssignment.getId(),
                    otherAssignment.getId(),
                    targetVerification.getId(),
                    otherVerification.getId(),
                    personalCategory.getId(),
                    personalRoutine.getId()
            );
        }
    }

    private record RollbackIds(
            Long groupId,
            Long ownerId,
            Long membershipId,
            Long categoryId,
            Long routineId,
            Long scheduleId,
            Long assignmentId,
            Long verificationId
    ) {
    }
}
