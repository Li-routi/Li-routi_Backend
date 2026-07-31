package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("그룹 통합 생성 롤백 테스트")
class GroupCreationRollbackIntegrationTest {
    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    @MockitoBean
    private GroupInviteCodeGenerator inviteCodeGenerator;

    @Test
    @DisplayName("OWNER 할당 저장이 실패하면 그룹부터 일정까지 통합 생성 전체를 롤백한다")
    void createGroup_AssignmentFailure_RollsBackEntireAggregate() {
        // given
        String suffix = Long.toString(System.nanoTime());
        String groupName = "롤백" + suffix.substring(suffix.length() - 6);
        String categoryName = "분류" + suffix.substring(suffix.length() - 4);
        String routineTitle = "루틴" + suffix.substring(suffix.length() - 5);
        Long memberId = new TransactionTemplate(transactionManager)
                .execute(status -> memberRepository.save(member(suffix)).getId());
        when(inviteCodeGenerator.generate()).thenReturn(
                new GroupInviteCodeGenerator.GeneratedInviteCode(
                        suffix.substring(suffix.length() - 7),
                        LocalDateTime.of(2026, 8, 1, 10, 10)
                )
        );
        doThrow(new IllegalStateException("assignment failure"))
                .when(groupRoutineAssignmentRepository)
                .insertIfAbsent(any(), any(), any(), any(), any(), any());
        GroupReqDTO.CreateGroup request = request(groupName, categoryName, routineTitle);

        try {
            // when & then
            assertThatThrownBy(() -> groupCommandService.createGroup(memberId, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("assignment failure");

            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.executeWithoutResult(status -> {
                assertThat(count("select count(g) from Group g where g.name = :value", groupName))
                        .isZero();
                assertThat(count(
                        "select count(gm) from GroupMember gm where gm.member.id = :value",
                        memberId
                )).isZero();
                assertThat(count(
                        "select count(c) from GroupRoutineCategory c where c.name = :value",
                        categoryName
                )).isZero();
                assertThat(count(
                        "select count(r) from GroupRoutine r where r.title = :value",
                        routineTitle
                )).isZero();
                assertThat(count(
                        "select count(s) from GroupRoutineSchedule s "
                                + "where s.groupRoutine.title = :value",
                        routineTitle
                )).isZero();
            });
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    memberRepository.deleteById(memberId));
        }
    }

    private long count(String jpql, Object value) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("value", value)
                .getSingleResult();
    }

    private GroupReqDTO.CreateGroup request(
            String groupName,
            String categoryName,
            String routineTitle
    ) {
        return new GroupReqDTO.CreateGroup(
                groupName,
                List.of(new GroupReqDTO.CreateGroupCategory(
                        "custom", categoryName, RoutineCategoryColor.BLUE
                )),
                List.of(new GroupReqDTO.CreateGroupRoutine(
                        null,
                        "custom",
                        routineTitle,
                        "롤백을 검증합니다.",
                        List.of(new GroupReqDTO.RoutineSchedule(
                                LocalDate.now(ZoneId.of("Asia/Seoul")).getDayOfWeek(),
                                LocalTime.of(9, 0),
                                LocalTime.of(10, 0)
                        ))
                ))
        );
    }

    private Member member(String suffix) {
        return Member.builder()
                .email("group-create-rollback-" + suffix + "@example.com")
                .nickname("롤백회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-create-rollback-" + suffix)
                .build();
    }
}
