package com.lirouti.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("GroupRoutineAssignmentRepository 제약 테스트")
class GroupRoutineAssignmentRepositoryTest {
    @Autowired
    private GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("같은 루틴을 같은 회원에게 두 번 할당할 수 없다")
    void save_DuplicateRoutineAndMember_ThrowsDataIntegrityViolation() {
        // given
        Group group = Group.builder().name("할당 그룹").inviteCode("ASG0001").build();
        RoutineCategory category = RoutineCategory.builder().name("할당 카테고리").active(true).build();
        Member member = Member.builder()
                .email("assignment@example.com")
                .nickname("할당회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("assignment-social")
                .build();
        em.persist(group);
        em.persist(category);
        em.persist(member);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("할당 루틴")
                .description("할당 테스트")
                .build();
        em.persist(routine);
        LocalDate assignedDate = LocalDate.of(2026, 7, 23);
        groupRoutineAssignmentRepository.saveAndFlush(assignment(routine, member, assignedDate));

        // when & then
        assertThatThrownBy(() -> groupRoutineAssignmentRepository
                .saveAndFlush(assignment(routine, member, assignedDate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("날짜별 할당은 멱등하며 할당 당시 시간을 스냅샷으로 저장한다")
    void insertIfAbsent_SameDate_IsIdempotentAndStoresSnapshot() {
        // given
        Group group = Group.builder().name("멱등 그룹").inviteCode("ASG0002").build();
        RoutineCategory category = RoutineCategory.builder().name("멱등 카테고리").active(true).build();
        Member member = Member.builder()
                .email("assignment-idempotent@example.com")
                .nickname("멱등회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("assignment-idempotent-social")
                .build();
        em.persist(group);
        em.persist(category);
        em.persist(member);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("멱등 루틴")
                .description("멱등 테스트")
                .build();
        em.persist(routine);
        em.flush();
        LocalDate assignedDate = LocalDate.of(2026, 7, 23);

        // when
        groupRoutineAssignmentRepository.insertIfAbsent(
                routine.getId(), member.getId(), assignedDate,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.PENDING.name()
        );
        groupRoutineAssignmentRepository.insertIfAbsent(
                routine.getId(), member.getId(), assignedDate,
                LocalTime.of(18, 0), LocalTime.of(19, 0),
                GroupRoutineAssignmentStatus.MISSED.name()
        );
        groupRoutineAssignmentRepository.insertIfAbsent(
                routine.getId(), member.getId(), assignedDate.plusDays(1),
                LocalTime.of(18, 0), LocalTime.of(19, 0),
                GroupRoutineAssignmentStatus.PENDING.name()
        );
        em.clear();

        // then
        assertThat(groupRoutineAssignmentRepository
                .findAllByMemberIdAndAssignedDate(member.getId(), assignedDate))
                .singleElement()
                .satisfies(assignment -> {
                    assertThat(assignment.getScheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
                    assertThat(assignment.getScheduledEndTime()).isEqualTo(LocalTime.of(10, 0));
                    assertThat(assignment.getStatus())
                            .isEqualTo(GroupRoutineAssignmentStatus.PENDING);
                });
        assertThat(groupRoutineAssignmentRepository
                .findAllByMemberIdAndAssignedDate(member.getId(), assignedDate.plusDays(1)))
                .singleElement()
                .satisfies(assignment -> {
                    assertThat(assignment.getScheduledStartTime()).isEqualTo(LocalTime.of(18, 0));
                    assertThat(assignment.getScheduledEndTime()).isEqualTo(LocalTime.of(19, 0));
                    assertThat(assignment.getStatus())
                            .isEqualTo(GroupRoutineAssignmentStatus.PENDING);
                });
    }

    @Test
    @DisplayName("시작한 할당은 IN_PROGRESS로, 마감한 할당은 MISSED로 전이한다")
    void refreshStatuses_TimeBoundary_TransitionsAtomically() {
        // given
        AssignmentFixture fixture = assignmentFixture("상태 전이", "status-transition");
        LocalDate assignedDate = LocalDate.of(2026, 7, 23);
        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository.saveAndFlush(
                assignment(
                        fixture.routine(),
                        fixture.member(),
                        assignedDate,
                        GroupRoutineAssignmentStatus.PENDING
                )
        );

        // when
        int started = groupRoutineAssignmentRepository.markStartedAssignmentsInProgress(
                assignedDate,
                LocalTime.of(9, 30),
                GroupRoutineAssignmentStatus.PENDING,
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
        int missed = groupRoutineAssignmentRepository.markExpiredAssignmentsMissed(
                assignedDate,
                LocalTime.of(10, 0),
                java.util.List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.MISSED
        );

        // then
        assertThat(started).isEqualTo(1);
        assertThat(missed).isEqualTo(1);
        assertThat(groupRoutineAssignmentRepository.findById(assignment.getId()))
                .get()
                .extracting(GroupRoutineAssignment::getStatus)
                .isEqualTo(GroupRoutineAssignmentStatus.MISSED);
    }

    @Test
    @DisplayName("인증과 마감 전이가 경합해도 먼저 확정된 COMPLETED를 MISSED가 덮어쓰지 않는다")
    void completeThenExpire_CompletedStatus_RemainsTerminal() {
        // given
        AssignmentFixture fixture = assignmentFixture("인증 경합", "completion-race");
        LocalDate assignedDate = LocalDate.of(2026, 7, 23);
        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository.saveAndFlush(
                assignment(
                        fixture.routine(),
                        fixture.member(),
                        assignedDate,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                )
        );

        // when
        int completed = groupRoutineAssignmentRepository.markCompletedIfInProgress(
                assignment.getId(),
                assignedDate,
                LocalTime.of(9, 59, 59),
                java.util.List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.COMPLETED
        );
        int missed = groupRoutineAssignmentRepository.markExpiredAssignmentsMissed(
                assignedDate,
                LocalTime.of(10, 0),
                java.util.List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.MISSED
        );

        // then
        assertThat(completed).isEqualTo(1);
        assertThat(missed).isZero();
        assertThat(groupRoutineAssignmentRepository.findById(assignment.getId()))
                .get()
                .extracting(GroupRoutineAssignment::getStatus)
                .isEqualTo(GroupRoutineAssignmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("마감 시각부터는 인증할 수 없고 MISSED 상태를 유지한다")
    void expireThenComplete_AtDeadline_CompletionIsRejected() {
        // given
        AssignmentFixture fixture = assignmentFixture("마감 경합", "deadline-race");
        LocalDate assignedDate = LocalDate.of(2026, 7, 23);
        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository.saveAndFlush(
                assignment(
                        fixture.routine(),
                        fixture.member(),
                        assignedDate,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                )
        );

        // when
        int missed = groupRoutineAssignmentRepository.markExpiredAssignmentsMissed(
                assignedDate,
                LocalTime.of(10, 0),
                java.util.List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.MISSED
        );
        int completed = groupRoutineAssignmentRepository.markCompletedIfInProgress(
                assignment.getId(),
                assignedDate,
                LocalTime.of(10, 0),
                java.util.List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.COMPLETED
        );

        // then
        assertThat(missed).isEqualTo(1);
        assertThat(completed).isZero();
        assertThat(groupRoutineAssignmentRepository.findById(assignment.getId()))
                .get()
                .extracting(GroupRoutineAssignment::getStatus)
                .isEqualTo(GroupRoutineAssignmentStatus.MISSED);
    }

    private GroupRoutineAssignment assignment(
            GroupRoutine routine,
            Member member,
            LocalDate assignedDate
    ) {
        return assignment(routine, member, assignedDate, GroupRoutineAssignmentStatus.PENDING);
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

    private AssignmentFixture assignmentFixture(String name, String identifier) {
        Group group = Group.builder()
                .name(name + " 그룹")
                .inviteCode((identifier + "0000000").substring(0, 7))
                .build();
        RoutineCategory category = RoutineCategory.builder()
                .name(name + " 카테고리")
                .active(true)
                .build();
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .nickname(name + "회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId(identifier + "-social")
                .build();
        em.persist(group);
        em.persist(category);
        em.persist(member);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(name + " 루틴")
                .description(name + " 테스트")
                .build();
        em.persist(routine);
        return new AssignmentFixture(routine, member);
    }

    private record AssignmentFixture(GroupRoutine routine, Member member) {
    }
}
