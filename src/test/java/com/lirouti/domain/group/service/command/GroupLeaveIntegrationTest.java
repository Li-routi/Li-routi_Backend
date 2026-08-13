package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.service.command.RoutineVerificationCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus.*;

@SpringBootTest
@DisplayName("그룹 방 나가기 통합 테스트")
class GroupLeaveIntegrationTest {
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<Long> verificationIds = new ArrayList<>();
    private final List<Long> assignmentIds = new ArrayList<>();
    private final List<Long> routineIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> membershipIds = new ArrayList<>();
    private final List<Long> groupIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @Autowired private GroupCommandService groupCommandService;
    @Autowired private GroupRoutineAssignmentCommandService assignmentCommandService;
    @Autowired private RoutineVerificationCommandService verificationCommandService;
    @Autowired private GroupValidationService groupValidationService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupRoutineCategoryRepository categoryRepository;
    @Autowired private GroupRoutineRepository routineRepository;
    @Autowired private GroupRoutineAssignmentRepository assignmentRepository;
    @Autowired private GroupRoutineVerificationRepository verificationRepository;
    @Autowired private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        verificationIds.forEach(id -> verificationRepository.findById(id).ifPresent(verificationRepository::delete));
        assignmentIds.forEach(id -> assignmentRepository.findById(id).ifPresent(assignmentRepository::delete));
        routineIds.forEach(id -> routineRepository.findById(id).ifPresent(routineRepository::delete));
        categoryIds.forEach(id -> categoryRepository.findById(id).ifPresent(categoryRepository::delete));
        membershipIds.forEach(id -> groupMemberRepository.findById(id).ifPresent(groupMemberRepository::delete));
        groupIds.forEach(id -> groupRepository.findById(id).ifPresent(groupRepository::delete));
        memberIds.forEach(id -> memberRepository.findById(id).ifPresent(memberRepository::delete));
    }

    @Test
    @DisplayName("ACTIVE MEMBER 탈퇴는 미완료 할당만 삭제하고 확정 이력과 다른 범위 데이터는 보존한다")
    void leaveGroup_ActiveMember_DeletesOnlyUnfinishedAssignments() {
        // given
        Fixture fixture = fixture();
        GroupRoutineAssignment pending = assignment(fixture.group(), fixture.member(), PENDING, LocalDate.now());
        GroupRoutineAssignment inProgress = assignment(fixture.group(), fixture.member(), IN_PROGRESS, LocalDate.now().minusDays(1));
        GroupRoutineAssignment completed = assignment(fixture.group(), fixture.member(), COMPLETED, LocalDate.now().minusDays(2));
        GroupRoutineAssignment missed = assignment(fixture.group(), fixture.member(), MISSED, LocalDate.now().minusDays(3));
        GroupRoutineVerification verification = verification(completed);
        Member otherMember = member();
        membership(fixture.group(), otherMember, GroupMemberRole.MEMBER);
        GroupRoutineAssignment otherMemberAssignment = assignment(fixture.group(), otherMember, PENDING, LocalDate.now());
        Group otherGroup = group();
        membership(otherGroup, fixture.member(), GroupMemberRole.MEMBER);
        GroupRoutineAssignment otherGroupAssignment = assignment(otherGroup, fixture.member(), IN_PROGRESS, LocalDate.now());

        // when
        groupCommandService.leaveGroup(fixture.group().getId(), fixture.member().getId());

        // then
        GroupMember persistedMembership = groupMemberRepository.findById(fixture.membership().getId()).orElseThrow();
        assertThat(persistedMembership.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        assertThat(persistedMembership.getLeftAt()).isNotNull();
        assertThat(assignmentRepository.existsById(pending.getId())).isFalse();
        assertThat(assignmentRepository.existsById(inProgress.getId())).isFalse();
        assertThat(assignmentRepository.existsById(completed.getId())).isTrue();
        assertThat(assignmentRepository.existsById(missed.getId())).isTrue();
        assertThat(verificationRepository.existsById(verification.getId())).isTrue();
        assertThat(assignmentRepository.existsById(otherMemberAssignment.getId())).isTrue();
        assertThat(assignmentRepository.existsById(otherGroupAssignment.getId())).isTrue();
    }

    @Test
    @DisplayName("OWNER와 이미 비활성인 구성원 및 비구성원은 탈퇴할 수 없다")
    void leaveGroup_IneligibleMember_ThrowsExistingError() {
        // given
        Group group = group();
        Member owner = member();
        Member left = member();
        Member kicked = member();
        Member outsider = member();
        GroupMember ownerMembership = membership(group, owner, GroupMemberRole.OWNER);
        GroupMember leftMembership = membership(group, left, GroupMemberRole.MEMBER);
        GroupMember kickedMembership = membership(group, kicked, GroupMemberRole.MEMBER);
        leftMembership.leave();
        kickedMembership.kick();
        groupMemberRepository.saveAll(List.of(leftMembership, kickedMembership));

        // when & then
        assertThatThrownBy(() -> groupCommandService.leaveGroup(group.getId(), owner.getId()))
                .isInstanceOf(GroupException.class)
                .extracting("code").isEqualTo(GroupErrorCode.OWNER_CANNOT_LEAVE);
        for (Member denied : List.of(left, kicked, outsider)) {
            assertThatThrownBy(() -> groupCommandService.leaveGroup(group.getId(), denied.getId()))
                    .isInstanceOf(GroupException.class)
                    .extracting("code").isEqualTo(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        }
        assertThat(groupMemberRepository.findById(ownerMembership.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("인증이 연결된 미완료 Assignment는 탈퇴 시 보존한다")
    void leaveGroup_VerifiedUnfinishedAssignment_IsPreserved() {
        // given
        Fixture fixture = fixture();
        GroupRoutineAssignment pending = assignment(fixture.group(), fixture.member(), PENDING, LocalDate.now());
        GroupRoutineVerification verification = verification(pending);

        // when
        groupCommandService.leaveGroup(fixture.group().getId(), fixture.member().getId());

        // then
        assertThat(groupMemberRepository.findById(fixture.membership().getId()).orElseThrow().getStatus())
                .isEqualTo(GroupMemberStatus.LEFT);
        assertThat(assignmentRepository.existsById(pending.getId())).isTrue();
        assertThat(verificationRepository.existsById(verification.getId())).isTrue();
    }

    @Test
    @DisplayName("인증이 먼저 그룹 잠금을 확정하면 탈퇴는 인증 이력을 보존한다")
    void leaveGroup_VerificationCommitsFirst_PreservesVerifiedAssignment() throws Exception {
        // given
        Fixture fixture = fixture();
        GroupRoutineAssignment assignment = verifiableAssignment(fixture);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch verificationLocked = new CountDownLatch(1);
        CountDownLatch releaseVerification = new CountDownLatch(1);
        CountDownLatch leaveStarted = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> verificationFuture = pool.submit(() -> transaction.executeWithoutResult(status -> {
                groupValidationService.lockActiveGroupForUpdate(fixture.group().getId());
                verificationLocked.countDown();
                await(releaseVerification);
                verificationCommandService.verifyGroupRoutine(
                        fixture.member().getId(), fixture.group().getId(), assignment.getGroupRoutine().getId(),
                        assignment.getAssignedDate(), "group-routine-verifications/lock-first.jpg", "완료",
                        LocalDateTime.now());
            }));

            assertThat(verificationLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> leaveFuture = pool.submit(() -> {
                leaveStarted.countDown();
                groupCommandService.leaveGroup(fixture.group().getId(), fixture.member().getId());
            });
            assertThat(leaveStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> leaveFuture.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseVerification.countDown();
            verificationFuture.get(10, TimeUnit.SECONDS);
            leaveFuture.get(10, TimeUnit.SECONDS);
        }

        // then
        assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getStatus())
                .isEqualTo(IN_PROGRESS);
        assertThat(verificationRepository.findByAssignmentId(assignment.getId())).isPresent();
        assertThat(groupMemberRepository.findById(fixture.membership().getId()).orElseThrow().getStatus())
                .isEqualTo(GroupMemberStatus.LEFT);
    }

    @Test
    @DisplayName("탈퇴의 bulk delete가 먼저 잠금을 확정하면 인증은 삭제된 할당을 찾지 못한다")
    void leaveGroup_LeaveCommitsFirst_VerificationFailsAfterDelete() throws Exception {
        // given
        Fixture fixture = fixture();
        GroupRoutineAssignment assignment = verifiableAssignment(fixture);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch releaseLeave = new CountDownLatch(1);
        CountDownLatch verificationStarted = new CountDownLatch(1);
        AtomicReference<Throwable> verificationFailure = new AtomicReference<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> leaveFuture = pool.submit(() -> transaction.executeWithoutResult(status -> {
                GroupMember groupMember = groupValidationService
                        .validateActiveGroupMember(fixture.group().getId(), fixture.member().getId());
                groupMember.leave();
                assignmentCommandService.deleteUnfinishedAssignmentsForLeaver(
                        fixture.group().getId(), fixture.member().getId());
                deleteLocked.countDown();
                await(releaseLeave);
            }));

            assertThat(deleteLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> verificationFuture = pool.submit(() -> {
                verificationStarted.countDown();
                try {
                    verificationCommandService.verifyGroupRoutine(
                            fixture.member().getId(), fixture.group().getId(), assignment.getGroupRoutine().getId(),
                            assignment.getAssignedDate(), "group-routine-verifications/leave-first.jpg", "완료",
                            LocalDateTime.now());
                } catch (Throwable throwable) {
                    verificationFailure.set(throwable);
                }
            });
            assertThat(verificationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> verificationFuture.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseLeave.countDown();
            leaveFuture.get(10, TimeUnit.SECONDS);
            verificationFuture.get(10, TimeUnit.SECONDS);
        }

        // then
        assertThat(verificationFailure.get()).isInstanceOf(VerificationException.class);
        assertThat(((VerificationException) verificationFailure.get()).getCode())
                .isEqualTo(VerificationErrorCode.ASSIGNMENT_NOT_FOUND);
        assertThat(assignmentRepository.existsById(assignment.getId())).isFalse();
        assertThat(verificationRepository.findByAssignmentId(assignment.getId())).isEmpty();
        assertThat(groupMemberRepository.findById(fixture.membership().getId()).orElseThrow().getStatus())
                .isEqualTo(GroupMemberStatus.LEFT);
    }

    private Fixture fixture() {
        Group group = group();
        Member member = member();
        GroupMember membership = membership(group, member, GroupMemberRole.MEMBER);
        return new Fixture(group, member, membership);
    }

    private GroupRoutineAssignment verifiableAssignment(Fixture fixture) {
        GroupRoutineAssignment assignment = assignment(
                fixture.group(), fixture.member(), IN_PROGRESS, LocalDate.now());
        return assignment;
    }

    private Group group() {
        int value = sequence.incrementAndGet();
        Group group = groupRepository.save(Group.builder()
                .name("탈퇴 테스트 그룹 " + value)
                .inviteCode(String.format("%07d", value))
                .build());
        groupIds.add(group.getId());
        return group;
    }

    private Member member() {
        int value = sequence.incrementAndGet();
        Member member = memberRepository.save(Member.builder()
                .email("group-leave-" + value + "@example.com")
                .nickname("탈퇴회원" + value)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-leave-social-" + value)
                .build());
        memberIds.add(member.getId());
        return member;
    }

    private GroupMember membership(Group group, Member member, GroupMemberRole role) {
        GroupMember membership = groupMemberRepository.save(GroupMember.builder()
                .group(group).member(member).role(role).build());
        membershipIds.add(membership.getId());
        return membership;
    }

    private GroupRoutineAssignment assignment(
            Group group,
            Member member,
            GroupRoutineAssignmentStatus status,
            LocalDate assignedDate
    ) {
        int value = sequence.incrementAndGet();
        GroupRoutineCategory category = categoryRepository.save(GroupRoutineCategory.builder()
                .group(group).name("탈퇴카테고리" + value).active(true).build());
        categoryIds.add(category.getId());
        GroupRoutine routine = routineRepository.save(GroupRoutine.builder()
                .group(group).category(category).title("탈퇴루틴" + value).description("설명").build());
        routineIds.add(routine.getId());
        GroupRoutineAssignment assignment = assignmentRepository.save(GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(member).assignedDate(assignedDate)
                .scheduledStartTime(LocalTime.MIN).scheduledEndTime(LocalTime.of(23, 59, 59)).status(status).build());
        assignmentIds.add(assignment.getId());
        return assignment;
    }

    private GroupRoutineVerification verification(GroupRoutineAssignment assignment) {
        GroupRoutineVerification verification = verificationRepository.save(GroupRoutineVerification.builder()
                .assignment(assignment).verifiedAt(LocalDateTime.now())
                .imageUrl("group-routine-verifications/history.jpg").content("기록").build());
        assignment.attachVerification(verification);
        verificationIds.add(verification.getId());
        return verification;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private record Fixture(Group group, Member member, GroupMember membership) {
    }
}
