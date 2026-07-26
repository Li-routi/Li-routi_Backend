package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.RoutineCategoryRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("그룹 루틴 할당 상태 동시성 테스트")
class GroupRoutineAssignmentConcurrencyTest {
    private static final LocalDate ASSIGNED_DATE = LocalDate.of(2099, 7, 23);

    @Autowired
    private GroupRoutineAssignmentCommandService assignmentCommandService;
    @Autowired
    private GroupRoutineAssignmentRepository assignmentRepository;
    @Autowired
    private GroupRoutineRepository groupRoutineRepository;
    @Autowired
    private RoutineCategoryRepository routineCategoryRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long assignmentId;
    private Long routineId;
    private Long categoryId;
    private Long groupId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());
        Member member = memberRepository.save(Member.builder()
                .email("assignment-concurrency-" + suffix + "@example.com")
                .nickname("동시성회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("assignment-concurrency-" + suffix)
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("할당 동시성 그룹")
                .inviteCode(suffix.substring(Math.max(0, suffix.length() - 7)))
                .build());
        RoutineCategory category = routineCategoryRepository.save(RoutineCategory.builder()
                .name("할당 동시성 카테고리-" + suffix)
                .active(true)
                .build());
        GroupRoutine routine = groupRoutineRepository.save(GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("할당 동시성 루틴")
                .description("동일 할당의 중복 인증을 검증합니다.")
                .build());
        GroupRoutineAssignment assignment = assignmentRepository.save(GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .assignedDate(ASSIGNED_DATE)
                .scheduledStartTime(LocalTime.of(9, 0))
                .scheduledEndTime(LocalTime.of(10, 0))
                .status(GroupRoutineAssignmentStatus.IN_PROGRESS)
                .build());

        assignmentId = assignment.getId();
        routineId = routine.getId();
        categoryId = category.getId();
        groupId = group.getId();
        memberId = member.getId();
    }

    @AfterEach
    void tearDown() {
        assignmentRepository.deleteById(assignmentId);
        groupRoutineRepository.deleteById(routineId);
        routineCategoryRepository.deleteById(categoryId);
        groupRepository.deleteById(groupId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("같은 할당을 동시에 인증해도 한 요청만 COMPLETED 처리한다")
    void completeAssignment_ConcurrentRequests_OnlyOneSucceeds() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyCompleted = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        LocalDateTime verifiedAt = ASSIGNED_DATE.atTime(9, 30);

        Runnable task = () -> {
            try {
                ready.countDown();
                start.await();
                assignmentCommandService.completeAssignment(assignmentId, verifiedAt);
                success.incrementAndGet();
            } catch (GroupException exception) {
                if (exception.getCode()
                        == GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_ALREADY_COMPLETED) {
                    alreadyCompleted.incrementAndGet();
                } else {
                    unexpected.incrementAndGet();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                unexpected.incrementAndGet();
            } finally {
                done.countDown();
            }
        };

        pool.submit(task);
        pool.submit(task);
        boolean workersReady = ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(workersReady).isTrue();
        assertThat(finished).isTrue();
        assertThat(success.get()).isEqualTo(1);
        assertThat(alreadyCompleted.get()).isEqualTo(1);
        assertThat(unexpected.get()).isZero();
        assertThat(assignmentRepository.findById(assignmentId))
                .get()
                .extracting(GroupRoutineAssignment::getStatus)
                .isEqualTo(GroupRoutineAssignmentStatus.COMPLETED);
    }
}
