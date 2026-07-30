package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DisplayName("그룹 루틴 수정 트랜잭션 통합 테스트")
class GroupRoutineUpdateIntegrationTest {
    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private GroupRoutineRepository groupRoutineRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private RoutineCategoryRepository routineCategoryRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private GroupRoutineAssignmentCommandService assignmentCommandService;

    @Test
    @DisplayName("할당 동기화가 실패하면 루틴 정보와 일정 변경을 모두 롤백한다")
    void updateRoutine_AssignmentSynchronizationFailure_RollsBackChanges() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Seed seed = transaction.execute(status -> createSeed());
        doThrow(new IllegalStateException("assignment synchronization failure"))
                .when(assignmentCommandService)
                .synchronizeRoutineAssignmentsToday(any(GroupRoutine.class));
        GroupReqDTO.UpdateRoutine request = new GroupReqDTO.UpdateRoutine(
                seed.newCategoryId(),
                "변경 루틴",
                "변경 설명",
                List.of(new GroupReqDTO.RoutineSchedule(
                        DayOfWeek.TUESDAY,
                        LocalTime.of(18, 0),
                        LocalTime.of(19, 0)
                ))
        );

        try {
            // when & then
            assertThatThrownBy(() -> groupCommandService.updateRoutine(
                    seed.groupId(),
                    seed.routineId(),
                    seed.memberId(),
                    request
            )).isInstanceOf(IllegalStateException.class);

            RoutineSnapshot snapshot = transaction.execute(status -> groupRoutineRepository
                    .findById(seed.routineId())
                    .map(this::snapshot)
                    .orElseThrow());
            assertThat(snapshot.categoryId()).isEqualTo(seed.originalCategoryId());
            assertThat(snapshot.title()).isEqualTo("기존 루틴");
            assertThat(snapshot.description()).isEqualTo("기존 설명");
            assertThat(snapshot.repeatDay()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(snapshot.startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(snapshot.endTime()).isEqualTo(LocalTime.of(10, 0));
        } finally {
            transaction.executeWithoutResult(status -> cleanup(seed));
        }
    }

    private Seed createSeed() {
        String suffix = Long.toString(System.nanoTime());
        Member member = memberRepository.save(Member.builder()
                .email("update-rollback-" + suffix + "@example.com")
                .nickname("수정롤백회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("update-rollback-social-" + suffix)
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("수정 롤백 그룹")
                .inviteCode(suffix.substring(Math.max(0, suffix.length() - 7)))
                .build());
        RoutineCategory originalCategory = routineCategoryRepository.save(RoutineCategory.builder()
                .name("수정 전 카테고리-" + suffix)
                .active(true)
                .build());
        RoutineCategory newCategory = routineCategoryRepository.save(RoutineCategory.builder()
                .name("수정 후 카테고리-" + suffix)
                .active(true)
                .build());
        GroupMember membership = groupMemberRepository.save(GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build());
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(originalCategory)
                .title("기존 루틴")
                .description("기존 설명")
                .build();
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        groupRoutineRepository.saveAndFlush(routine);
        return new Seed(
                member.getId(),
                group.getId(),
                originalCategory.getId(),
                newCategory.getId(),
                membership.getId(),
                routine.getId()
        );
    }

    private RoutineSnapshot snapshot(GroupRoutine routine) {
        GroupRoutineSchedule schedule = routine.getSchedules().getFirst();
        return new RoutineSnapshot(
                routine.getCategory().getId(),
                routine.getTitle(),
                routine.getDescription(),
                schedule.getRepeatDay(),
                schedule.getStartTime(),
                schedule.getEndTime()
        );
    }

    private void cleanup(Seed seed) {
        groupRoutineRepository.deleteById(seed.routineId());
        groupMemberRepository.deleteById(seed.membershipId());
        routineCategoryRepository.deleteById(seed.newCategoryId());
        routineCategoryRepository.deleteById(seed.originalCategoryId());
        groupRepository.deleteById(seed.groupId());
        memberRepository.deleteById(seed.memberId());
    }

    private record Seed(
            Long memberId,
            Long groupId,
            Long originalCategoryId,
            Long newCategoryId,
            Long membershipId,
            Long routineId
    ) {
    }

    private record RoutineSnapshot(
            Long categoryId,
            String title,
            String description,
            DayOfWeek repeatDay,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }
}
