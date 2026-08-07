package com.lirouti.domain.group.service.command;

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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@DisplayName("그룹 루틴 삭제와 할당 생성 동시성 테스트")
class GroupRoutineDeletionConcurrencyTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private GroupRoutineAssignmentRepository assignmentRepository;
    @Autowired
    private GroupRoutineRepository routineRepository;
    @Autowired
    private GroupRoutineCategoryRepository categoryRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private GroupRoutineAssignmentCommandService assignmentCommandService;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("루틴 쓰기 잠금 동안 INSERT SELECT가 대기하고 삭제 커밋 후 0건을 반환한다")
    void deleteRoutine_ConcurrentInsert_SerializesAndDoesNotCreateAssignment() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Seed seed = transaction.execute(status -> createSeed());
        CountDownLatch routineLockAcquired = new CountDownLatch(1);
        CountDownLatch allowDeleteToContinue = new CountDownLatch(1);
        CountDownLatch insertAttempted = new CountDownLatch(1);
        CountDownLatch insertFinished = new CountDownLatch(1);

        doAnswer(invocation -> {
            routineLockAcquired.countDown();
            if (!allowDeleteToContinue.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to continue deletion");
            }
            return invocation.callRealMethod();
        }).when(assignmentCommandService).deleteMutableAssignments(seed.routineId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> deleteFuture = executor.submit(() -> groupCommandService.deleteRoutine(
                    seed.groupId(), seed.routineId(), seed.ownerId()));
            assertThat(routineLockAcquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<Integer> insertFuture = executor.submit(() -> {
                insertAttempted.countDown();
                try {
                    return transaction.execute(status -> assignmentRepository.insertIfAbsent(
                            seed.routineId(),
                            seed.memberId(),
                            LocalDate.of(2026, 8, 4),
                            LocalTime.of(9, 0),
                            LocalTime.of(10, 0),
                            GroupRoutineAssignmentStatus.PENDING.name()
                    ));
                } finally {
                    insertFinished.countDown();
                }
            });
            assertThat(insertAttempted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            // 루틴의 PESSIMISTIC_WRITE 잠금이 풀리기 전에는 INSERT ... SELECT가 완료되면 안 된다.
            assertThat(insertFinished.await(500, TimeUnit.MILLISECONDS)).isFalse();

            allowDeleteToContinue.countDown();
            deleteFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(insertFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isZero();

            DeletionSnapshot snapshot = transaction.execute(status -> deletionSnapshot(seed));
            assertThat(snapshot.routineActive()).isFalse();
            assertThat(snapshot.assignmentCount()).isZero();
        } finally {
            allowDeleteToContinue.countDown();
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            transaction.executeWithoutResult(status -> cleanup(seed));
        }
    }

    private Seed createSeed() {
        String suffix = Long.toString(System.nanoTime());
        Member owner = memberRepository.save(Member.builder()
                .email("delete-concurrency-owner-" + suffix + "@example.com")
                .nickname("삭제경합방장")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("delete-concurrency-owner-" + suffix)
                .build());
        Member member = memberRepository.save(Member.builder()
                .email("delete-concurrency-member-" + suffix + "@example.com")
                .nickname("삭제경합회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("delete-concurrency-member-" + suffix)
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("삭제 경합 그룹")
                .inviteCode(suffix.substring(suffix.length() - 7))
                .build());
        GroupMember ownerMembership = groupMemberRepository.save(GroupMember.builder()
                .member(owner)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build());
        GroupMember memberMembership = groupMemberRepository.save(GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.MEMBER)
                .build());
        GroupRoutineCategory category = categoryRepository.save(GroupRoutineCategory.builder()
                .group(group)
                .name("삭제 경합 카테고리")
                .active(true)
                .build());
        GroupRoutine routine = routineRepository.saveAndFlush(GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("삭제 경합 루틴")
                .description("삭제와 할당 생성 경합")
                .build());
        return new Seed(
                owner.getId(), member.getId(), group.getId(),
                ownerMembership.getId(), memberMembership.getId(), category.getId(), routine.getId()
        );
    }

    private DeletionSnapshot deletionSnapshot(Seed seed) {
        em.clear();
        boolean active = routineRepository.findById(seed.routineId())
                .map(GroupRoutine::getActive)
                .orElseThrow();
        long assignmentCount = em.createQuery(
                        "select count(assignment) from GroupRoutineAssignment assignment "
                                + "where assignment.groupRoutine.id = :routineId",
                        Long.class
                )
                .setParameter("routineId", seed.routineId())
                .getSingleResult();
        return new DeletionSnapshot(active, assignmentCount);
    }

    private void cleanup(Seed seed) {
        assignmentRepository.deleteAll(em.createQuery(
                        "select assignment from GroupRoutineAssignment assignment "
                                + "where assignment.groupRoutine.id = :routineId",
                        GroupRoutineAssignment.class
                )
                .setParameter("routineId", seed.routineId())
                .getResultList());
        routineRepository.deleteById(seed.routineId());
        groupMemberRepository.deleteAllById(List.of(
                seed.ownerMembershipId(), seed.memberMembershipId()));
        categoryRepository.deleteById(seed.categoryId());
        groupRepository.deleteById(seed.groupId());
        memberRepository.deleteAllById(List.of(seed.ownerId(), seed.memberId()));
    }

    private record Seed(
            Long ownerId,
            Long memberId,
            Long groupId,
            Long ownerMembershipId,
            Long memberMembershipId,
            Long categoryId,
            Long routineId
    ) {
    }

    private record DeletionSnapshot(boolean routineActive, long assignmentCount) {
    }
}
