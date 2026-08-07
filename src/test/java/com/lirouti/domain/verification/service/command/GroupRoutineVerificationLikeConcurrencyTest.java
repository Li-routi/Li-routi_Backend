package com.lirouti.domain.verification.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;

/**
 * 같은 회원의 좋아요 요청은 DB UNIQUE 제약과 ON DUPLICATE KEY UPDATE로 직렬화한다.
 * 이 테스트는 각 스레드의 트랜잭션을 실제 커밋해야 하므로 클래스에 @Transactional을 붙이지 않는다.
 */
@SpringBootTest
@DisplayName("그룹 루틴 인증 게시물 좋아요 동시성 테스트")
class GroupRoutineVerificationLikeConcurrencyTest {
    @Autowired private GroupRoutineVerificationLikeCommandService likeCommandService;
    @Autowired private GroupCommandService groupCommandService;
    @MockitoSpyBean private GroupValidationService groupValidationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupRoutineCategoryRepository categoryRepository;
    @Autowired private GroupRoutineRepository routineRepository;
    @Autowired private GroupRoutineAssignmentRepository assignmentRepository;
    @Autowired private GroupRoutineVerificationRepository verificationRepository;
    @Autowired private GroupRoutineVerificationLikeRepository likeRepository;

    private Long groupId;
    private Long memberId;
    private Long ownerId;
    private Long anotherMemberId;
    private Long verificationId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.builder()
                .email("group-like-concurrency@ex.com").nickname("동시성회원")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("group-like-concurrency").build());
        Group group = groupRepository.save(Group.builder()
                .name("동시성 좋아요 그룹").inviteCode("GLCONC").build());
        Member owner = memberRepository.save(Member.builder()
                .email("group-like-concurrency-owner@ex.com").nickname("동시성방장")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("group-like-concurrency-owner").build());
        groupMemberRepository.save(GroupMember.builder()
                .group(group).member(owner).role(GroupMemberRole.OWNER).build());
        groupMemberRepository.save(GroupMember.builder()
                .group(group).member(member).role(GroupMemberRole.MEMBER).build());
        GroupRoutineCategory category = categoryRepository.save(GroupRoutineCategory.builder()
                .group(group).name("동시성 카테고리").active(true).build());
        GroupRoutine routine = routineRepository.save(GroupRoutine.builder()
                .group(group).category(category).title("동시성 루틴").description("설명").build());
        GroupRoutineAssignment assignment = assignmentRepository.save(GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(member).assignedDate(LocalDate.now())
                .scheduledStartTime(LocalTime.MIDNIGHT).scheduledEndTime(LocalTime.of(23, 59, 59))
                .status(GroupRoutineAssignmentStatus.COMPLETED).build());
        GroupRoutineVerification verification = GroupRoutineVerification.builder()
                .assignment(assignment).verifiedAt(LocalDateTime.now())
                .imageUrl("group-routine-verifications/concurrency.jpg").content("완료").build();

        verification = verificationRepository.save(verification);

        groupId = group.getId();
        memberId = member.getId();
        ownerId = owner.getId();
        verificationId = verification.getId();
    }

    @AfterEach
    void tearDown() {
        reset(groupValidationService);
        verificationRepository.deleteById(verificationId);
        groupRepository.deleteById(groupId);
        memberRepository.deleteById(memberId);
        memberRepository.deleteById(ownerId);
        if (anotherMemberId != null) {
            memberRepository.deleteById(anotherMemberId);
        }
    }

    @Test
    @DisplayName("같은 좋아요 POST가 동시에 들어와도 모두 성공하고 Like 행은 하나다")
    void concurrentLike_BothSucceed_SingleRow() throws InterruptedException {
        Result result = runConcurrently(() -> likeCommandService.like(memberId, groupId, verificationId));

        assertThat(result.failure()).isNull();
        assertThat(result.success()).isEqualTo(2);
        assertThat(likeRepository.countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 좋아요 DELETE가 동시에 들어와도 모두 성공하고 Like 행은 남지 않는다")
    void concurrentUnlike_BothSucceed_NoRow() throws InterruptedException {
        likeCommandService.like(memberId, groupId, verificationId);

        Result result = runConcurrently(() -> likeCommandService.unlike(memberId, groupId, verificationId));

        assertThat(result.failure()).isNull();
        assertThat(result.success()).isEqualTo(2);
        assertThat(likeRepository.countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isZero();
    }

    @Test
    @DisplayName("서로 다른 회원의 좋아요가 동시에 들어와도 두 Like 행이 모두 보존된다")
    void concurrentLikes_FromDifferentMembers_AreBothCounted() throws InterruptedException {
        Member anotherMember = memberRepository.save(Member.builder()
                .email("group-like-concurrency-another@ex.com").nickname("다른동시성회원")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("group-like-concurrency-another").build());
        groupMemberRepository.save(GroupMember.builder()
                .group(groupRepository.getReferenceById(groupId)).member(anotherMember)
                .role(GroupMemberRole.MEMBER).build());
        anotherMemberId = anotherMember.getId();

        Result result = runConcurrently(
                () -> likeCommandService.like(memberId, groupId, verificationId),
                () -> likeCommandService.like(anotherMember.getId(), groupId, verificationId));

        assertThat(result.failure()).isNull();
        assertThat(result.success()).isEqualTo(2);
        assertThat(likeRepository.countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isEqualTo(2);
    }

    @Test
    @DisplayName("좋아요와 취소가 경합해도 최종 Like 행 수와 조회 가능한 좋아요 상태가 일치한다")
    void concurrentLikeAndUnlike_FinalStateIsConsistent() throws InterruptedException {
        likeCommandService.like(memberId, groupId, verificationId);

        Result result = runConcurrently(
                () -> likeCommandService.like(memberId, groupId, verificationId),
                () -> likeCommandService.unlike(memberId, groupId, verificationId));

        assertThat(result.failure()).isNull();
        assertThat(result.success()).isEqualTo(2);

        long likeCount = likeRepository.countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L);
        boolean liked = likeRepository.findLikedVerificationIds(List.of(verificationId), memberId)
                .contains(verificationId);
        assertThat(likeCount).isIn(0L, 1L);
        assertThat(likeCount == 1).isEqualTo(liked);
    }

    @Test
    @DisplayName("좋아요의 ACTIVE 검증 뒤 탈퇴는 그룹 잠금 해제 전까지 대기한다")
    void likeThenLeave_LeaveWaitsForLikeGroupLock() throws Exception {
        MembershipRaceResult result = runMembershipRaceAfterLikeMembershipValidation(
                () -> likeCommandService.like(memberId, groupId, verificationId),
                () -> groupCommandService.leaveGroup(groupId, memberId));

        assertThat(result.exitFailure()).isNull();
        assertThat(result.likeFailure()).isNull();
        assertThat(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).orElseThrow()
                .getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        assertThat(result.likeSucceeded()).isTrue();
        assertThat(likeRepository.countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isEqualTo(1L);
        assertThatThrownBy(() -> likeCommandService.like(memberId, groupId, verificationId))
                .isInstanceOf(GroupException.class)
                .hasFieldOrPropertyWithValue("code", GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("좋아요의 ACTIVE 검증 뒤 강제퇴장은 그룹 잠금 해제 전까지 대기한다")
    void likeThenKick_KickWaitsForLikeGroupLock() throws Exception {
        MembershipRaceResult result = runMembershipRaceAfterLikeMembershipValidation(
                () -> likeCommandService.like(memberId, groupId, verificationId),
                () -> groupCommandService.kickMember(groupId, ownerId, memberId));

        assertThat(result.exitFailure()).isNull();
        assertThat(result.likeFailure()).isNull();
        assertThat(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).orElseThrow()
                .getStatus()).isEqualTo(GroupMemberStatus.KICKED);
        assertThat(result.likeSucceeded()).isTrue();
        assertThat(likeRepository.countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isEqualTo(1L);
        assertThatThrownBy(() -> likeCommandService.like(memberId, groupId, verificationId))
                .isInstanceOf(GroupException.class)
                .hasFieldOrPropertyWithValue("code", GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("좋아요 취소의 ACTIVE 검증 뒤 탈퇴는 그룹 잠금 해제 전까지 대기한다")
    void unlikeThenLeave_LeaveWaitsForUnlikeGroupLock() throws Exception {
        likeCommandService.like(memberId, groupId, verificationId);

        MembershipRaceResult result = runMembershipRaceAfterLikeMembershipValidation(
                () -> likeCommandService.unlike(memberId, groupId, verificationId),
                () -> groupCommandService.leaveGroup(groupId, memberId));

        assertThat(result.exitFailure()).isNull();
        assertThat(result.likeFailure()).isNull();
        assertThat(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).orElseThrow()
                .getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        assertThat(result.likeSucceeded()).isTrue();
        assertThat(likeRepository.countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L)).isZero();
        assertThatThrownBy(() -> likeCommandService.unlike(memberId, groupId, verificationId))
                .isInstanceOf(GroupException.class)
                .hasFieldOrPropertyWithValue("code", GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    /**
     * 좋아요 명령이 그룹 행 잠금과 ACTIVE 검증을 마친 직후 멈춘다. 이때 탈퇴·강제퇴장은
     * 같은 그룹 행 PESSIMISTIC_WRITE 잠금에서 기다려야 한다.
     */
    private MembershipRaceResult runMembershipRaceAfterLikeMembershipValidation(
            Runnable likeCommand,
            Runnable exitCommand
    ) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch membershipValidated = new CountDownLatch(1);
        CountDownLatch allowLikeToContinue = new CountDownLatch(1);
        CountDownLatch exitStarted = new CountDownLatch(1);
        AtomicReference<Throwable> likeFailure = new AtomicReference<>();
        AtomicReference<Throwable> exitFailure = new AtomicReference<>();
        AtomicInteger likeSucceeded = new AtomicInteger();

        doAnswer(invocation -> {
            Object groupMember = invocation.callRealMethod();
            membershipValidated.countDown();
            if (!allowLikeToContinue.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to continue like command");
            }
            return groupMember;
        }).doCallRealMethod().when(groupValidationService)
                .validateActiveGroupMember(groupId, memberId);

        try {
            Future<?> likeFuture = pool.submit(() -> runRaceTask(
                    likeCommand, likeSucceeded, likeFailure));
            assertThat(membershipValidated.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> exitFuture = pool.submit(() -> {
                exitStarted.countDown();
                runRaceTask(exitCommand, null, exitFailure);
            });
            assertThat(exitStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // 좋아요가 검증을 마쳤지만 아직 반환되지 않았다. 탈퇴·강퇴는 그룹 행 잠금 때문에 대기한다.
            assertThatThrownBy(() -> exitFuture.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowLikeToContinue.countDown();
            likeFuture.get(10, TimeUnit.SECONDS);
            exitFuture.get(10, TimeUnit.SECONDS);
        } finally {
            allowLikeToContinue.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
        return new MembershipRaceResult(likeSucceeded.get() == 1, likeFailure.get(), exitFailure.get());
    }

    private void runRaceTask(
            Runnable task,
            AtomicInteger success,
            AtomicReference<Throwable> failure
    ) {
        try {
            task.run();
            if (success != null) {
                success.incrementAndGet();
            }
        } catch (RuntimeException e) {
            failure.compareAndSet(null, e);
        }
    }

    private Result runConcurrently(Runnable task) throws InterruptedException {
        return runConcurrently(task, task);
    }

    private Result runConcurrently(Runnable firstTask, Runnable secondTask) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (Runnable task : List.of(firstTask, secondTask)) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    task.run();
                    success.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure.compareAndSet(null, e);
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
        }

        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).as("두 스레드가 게이트에 도착").isTrue();
        assertThat(finished).as("20초 안에 완료").isTrue();
        return new Result(success.get(), failure.get());
    }

    private record Result(int success, Throwable failure) {}

    private record MembershipRaceResult(boolean likeSucceeded, Throwable likeFailure, Throwable exitFailure) {}
}
