package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.RoutineCategoryRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DisplayName("그룹 루틴 생성 트랜잭션 통합 테스트")
class GroupRoutineCreationIntegrationTest {
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
    private GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;

    @Test
    @DisplayName("할당 저장이 실패하면 루틴과 일정도 모두 롤백한다")
    void createRoutine_AssignmentFailure_RollsBackRoutineAndSchedules() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Seed seed = transaction.execute(status -> createSeed());
        doThrow(new IllegalStateException("assignment failure"))
                .when(groupRoutineAssignmentRepository)
                .insertIfAbsent(any(), any(), any(), any(), any(), any());
        GroupReqDTO.CreateRoutine request = new GroupReqDTO.CreateRoutine(
                seed.categoryId(),
                "롤백 루틴",
                "할당 실패 시 모두 롤백합니다.",
                List.of(new GroupReqDTO.RoutineSchedule(
                        LocalDate.now(ZoneId.of("Asia/Seoul")).getDayOfWeek(),
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0)
                ))
        );

        try {
            // when & then
            assertThatThrownBy(() -> groupCommandService.createRoutine(
                    seed.groupId(),
                    seed.memberId(),
                    request
            )).isInstanceOf(IllegalStateException.class);
            assertThat(groupRoutineRepository
                    .existsByGroupIdAndTitle(seed.groupId(), "롤백 루틴"))
                    .isFalse();
        } finally {
            transaction.executeWithoutResult(status -> cleanup(seed));
        }
    }

    private Seed createSeed() {
        String suffix = Long.toString(System.nanoTime());
        Member member = memberRepository.save(Member.builder()
                .email("rollback-" + suffix + "@example.com")
                .nickname("롤백회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("rollback-social-" + suffix)
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("롤백 그룹")
                .inviteCode(suffix.substring(Math.max(0, suffix.length() - 7)))
                .build());
        RoutineCategory category = routineCategoryRepository.save(RoutineCategory.builder()
                .name("롤백 카테고리-" + suffix)
                .active(true)
                .build());
        GroupMember membership = groupMemberRepository.save(GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.OWNER)
                .build());
        return new Seed(member.getId(), group.getId(), category.getId(), membership.getId());
    }

    private void cleanup(Seed seed) {
        groupMemberRepository.deleteById(seed.membershipId());
        routineCategoryRepository.deleteById(seed.categoryId());
        groupRepository.deleteById(seed.groupId());
        memberRepository.deleteById(seed.memberId());
    }

    private record Seed(Long memberId, Long groupId, Long categoryId, Long membershipId) {
    }
}
