package com.lirouti.domain.verification.service;

import static com.lirouti.support.testdb.MemberFixtureCleanup.deleteDependencies;
import static org.assertj.core.api.Assertions.assertThat;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.service.command.RoutineVerificationCommandService;
import com.lirouti.global.util.TimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@DisplayName("그룹 최근 인증 등록 시각 동시성 테스트")
class GroupLastVerificationAtConcurrencyTest {
    @Autowired private RoutineVerificationCommandService verificationCommandService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupRoutineCategoryRepository categoryRepository;
    @Autowired private GroupRoutineRepository routineRepository;
    @Autowired private GroupRoutineAssignmentRepository assignmentRepository;
    @Autowired private GroupRoutineVerificationRepository verificationRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long groupId;
    private Long memberId;
    private Long categoryId;
    private List<Long> routineIds;
    private List<Long> assignmentIds;

    @AfterEach
    void tearDown() {
        if (assignmentIds != null) {
            assignmentIds.forEach(assignmentId -> verificationRepository.findByAssignmentId(assignmentId)
                    .ifPresent(verificationRepository::delete));
            assignmentIds.forEach(assignmentId -> assignmentRepository.findById(assignmentId)
                    .ifPresent(assignmentRepository::delete));
        }
        if (routineIds != null) {
            routineIds.forEach(routineId -> routineRepository.findById(routineId)
                    .ifPresent(routineRepository::delete));
        }
        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(categoryRepository::delete);
        }
        if (groupId != null) {
            groupMemberRepository.findAll().stream()
                    .filter(membership -> membership.getGroup().getId().equals(groupId))
                    .forEach(groupMemberRepository::delete);
            groupRepository.findById(groupId).ifPresent(groupRepository::delete);
        }
        if (memberId != null) {
            deleteDependencies(jdbcTemplate, memberId);
            memberRepository.findById(memberId).ifPresent(memberRepository::delete);
        }
    }

    @Test
    @DisplayName("같은 그룹의 동시 신규 인증 후 마지막 등록 시각은 저장된 createdAt의 최댓값이다")
    void verifyGroupRoutine_ConcurrentRequests_KeepsLatestCreatedAt() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                int routineIndex = index;
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        verificationCommandService.verifyGroupRoutine(
                                fixture.memberId(), fixture.groupId(), fixture.routineIds().get(routineIndex),
                                fixture.assignedDate(),
                                "group-routine-verifications/concurrency-" + routineIndex + ".jpg",
                                "동시 인증", LocalDateTime.now(TimeUtil.KST));
                        successCount.incrementAndGet();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successCount).hasValue(2);
        List<GroupRoutineVerification> verifications = assignmentIds.stream()
                .map(assignmentId -> verificationRepository.findByAssignmentId(assignmentId).orElseThrow())
                .toList();
        LocalDateTime latestCreatedAt = verifications.stream()
                .map(GroupRoutineVerification::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElseThrow();

        assertThat(groupRepository.findById(groupId).orElseThrow().getLastVerificationAt())
                .isEqualTo(latestCreatedAt);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Group group = groupRepository.save(Group.builder()
                .name("동시 인증 그룹")
                .inviteCode(suffix.substring(0, 7).toUpperCase())
                .build());
        Member member = memberRepository.save(Member.builder()
                .email(suffix + "@example.com")
                .nickname("동시인증회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId(suffix)
                .build());
        groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .member(member)
                .role(GroupMemberRole.MEMBER)
                .build());
        GroupRoutineCategory category = categoryRepository.save(GroupRoutineCategory.builder()
                .group(group)
                .name("동시 인증 카테고리")
                .active(true)
                .build());
        LocalDate assignedDate = LocalDate.now(TimeUtil.KST);
        GroupRoutine firstRoutine = routineRepository.save(routine(group, category, "첫 번째 인증"));
        GroupRoutine secondRoutine = routineRepository.save(routine(group, category, "두 번째 인증"));
        GroupRoutineAssignment firstAssignment = assignmentRepository.save(assignment(firstRoutine, member, assignedDate));
        GroupRoutineAssignment secondAssignment = assignmentRepository.save(assignment(secondRoutine, member, assignedDate));

        groupId = group.getId();
        memberId = member.getId();
        categoryId = category.getId();
        routineIds = List.of(firstRoutine.getId(), secondRoutine.getId());
        assignmentIds = List.of(firstAssignment.getId(), secondAssignment.getId());
        return new Fixture(groupId, memberId, routineIds, assignedDate);
    }

    private GroupRoutine routine(Group group, GroupRoutineCategory category, String title) {
        return GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description(title + " 설명")
                .build();
    }

    private GroupRoutineAssignment assignment(GroupRoutine routine, Member member, LocalDate assignedDate) {
        return GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .assignedDate(assignedDate)
                .scheduledStartTime(LocalTime.MIDNIGHT)
                .scheduledEndTime(LocalTime.of(23, 59, 59))
                .status(GroupRoutineAssignmentStatus.IN_PROGRESS)
                .build();
    }

    private record Fixture(Long groupId, Long memberId, List<Long> routineIds, LocalDate assignedDate) {
    }
}
