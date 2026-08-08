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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@DisplayName("그룹 루틴 삭제 롤백 통합 테스트")
class GroupRoutineDeletionRollbackIntegrationTest {
    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private GroupRoutineRepository routineRepository;
    @Autowired
    private GroupRoutineAssignmentRepository assignmentRepository;
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
    @DisplayName("실제 bulk delete 후 예외가 발생하면 루틴과 미확정 할당이 모두 복원된다")
    void deleteRoutine_ExceptionAfterBulkDelete_RollsBackEntireTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Seed seed = transaction.execute(status -> createSeed());
        AtomicBoolean actualBulkDeleteCompleted = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            actualBulkDeleteCompleted.set(true);
            throw new IllegalStateException("failure after assignment deletion");
        }).when(assignmentCommandService).deleteMutableAssignments(seed.routineId());

        try {
            // when & then
            assertThatThrownBy(() -> groupCommandService.deleteRoutine(
                    seed.groupId(), seed.routineId(), seed.ownerId()
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessage("failure after assignment deletion");
            assertThat(actualBulkDeleteCompleted).isTrue();

            RollbackSnapshot snapshot = transaction.execute(status -> rollbackSnapshot(seed));
            assertThat(snapshot.routineActive()).isTrue();
            assertThat(snapshot.assignmentStatuses())
                    .containsExactlyInAnyOrder(
                            GroupRoutineAssignmentStatus.PENDING,
                            GroupRoutineAssignmentStatus.IN_PROGRESS
                    );
        } finally {
            transaction.executeWithoutResult(status -> cleanup(seed));
        }
    }

    private Seed createSeed() {
        String suffix = Long.toString(System.nanoTime());
        Member owner = memberRepository.save(Member.builder()
                .email("delete-rollback-owner-" + suffix + "@example.com")
                .nickname("삭제롤백방장")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("delete-rollback-owner-" + suffix)
                .build());
        Member member = memberRepository.save(Member.builder()
                .email("delete-rollback-member-" + suffix + "@example.com")
                .nickname("삭제롤백회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("delete-rollback-member-" + suffix)
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("삭제 롤백 그룹")
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
                .name("삭제 롤백 카테고리")
                .active(true)
                .build());
        GroupRoutine routine = routineRepository.save(GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("삭제 롤백 루틴")
                .description("삭제 롤백 검증")
                .build());
        LocalDate assignedDate = LocalDate.of(2026, 8, 4);
        assignmentRepository.save(assignment(
                routine, owner, assignedDate, GroupRoutineAssignmentStatus.PENDING));
        assignmentRepository.save(assignment(
                routine, member, assignedDate, GroupRoutineAssignmentStatus.IN_PROGRESS));
        assignmentRepository.flush();
        return new Seed(
                owner.getId(), member.getId(), group.getId(),
                ownerMembership.getId(), memberMembership.getId(), category.getId(), routine.getId()
        );
    }

    private RollbackSnapshot rollbackSnapshot(Seed seed) {
        em.clear();
        boolean routineActive = routineRepository.findById(seed.routineId())
                .map(GroupRoutine::getActive)
                .orElseThrow();
        List<GroupRoutineAssignmentStatus> statuses = em.createQuery(
                        "select assignment.status from GroupRoutineAssignment assignment "
                                + "where assignment.groupRoutine.id = :routineId",
                        GroupRoutineAssignmentStatus.class
                )
                .setParameter("routineId", seed.routineId())
                .getResultList();
        return new RollbackSnapshot(routineActive, statuses);
    }

    private GroupRoutineAssignment assignment(
            GroupRoutine routine,
            Member member,
            LocalDate assignedDate,
            GroupRoutineAssignmentStatus status
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

    private record RollbackSnapshot(
            boolean routineActive,
            List<GroupRoutineAssignmentStatus> assignmentStatuses
    ) {
    }
}
