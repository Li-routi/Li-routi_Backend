package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.repository.GroupRepository;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.lirouti.support.testdb.MemberFixtureCleanup.deleteDependencies;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("그룹 통합 생성 초대코드 재시도 통합 테스트")
class GroupCreationInviteCodeRetryIntegrationTest {
    private static final String COLLIDING_CODE = "CLASH01";
    private static final String SUCCESS_CODE = "FRESH01";

    @Autowired
    private GroupCommandService groupCommandService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private GroupInviteCodeGenerator inviteCodeGenerator;

    @Test
    @DisplayName("첫 unique 충돌 시도를 롤백하고 두 번째 시도의 데이터 한 세트만 저장한다")
    void createGroup_FirstInviteCodeConflict_RollsBackAndStoresOneAggregate() {
        String suffix = Long.toString(System.nanoTime());
        String groupName = "재시도" + suffix.substring(suffix.length() - 6);
        String categoryName = "분류" + suffix.substring(suffix.length() - 4);
        String routineTitle = "루틴" + suffix.substring(suffix.length() - 5);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long memberId = transaction.execute(status ->
                memberRepository.save(member(suffix)).getId());
        Long collisionGroupId = transaction.execute(status -> groupRepository.saveAndFlush(
                Group.builder()
                        .name("충돌코드보유")
                        .inviteCode(COLLIDING_CODE)
                        .build()
        ).getId());
        when(inviteCodeGenerator.generate())
                .thenReturn(COLLIDING_CODE)
                .thenReturn(SUCCESS_CODE);

        try {
            GroupResDTO.CreateResult result = groupCommandService.createGroup(
                    memberId,
                    request(groupName, categoryName, routineTitle)
            );

            assertThat(result.name()).isEqualTo(groupName);
            assertThat(result.assignmentCount()).isEqualTo(1);
            verify(inviteCodeGenerator, times(2)).generate();

            transaction.executeWithoutResult(status -> {
                assertThat(count("select count(g) from Group g where g.name = :value", groupName))
                        .isEqualTo(1);
                assertThat(count(
                        "select count(gm) from GroupMember gm where gm.member.id = :value",
                        memberId
                )).isEqualTo(1);
                assertThat(count(
                        "select count(c) from GroupRoutineCategory c where c.name = :value",
                        categoryName
                )).isEqualTo(1);
                assertThat(count(
                        "select count(r) from GroupRoutine r where r.title = :value",
                        routineTitle
                )).isEqualTo(1);
                assertThat(count(
                        "select count(s) from GroupRoutineSchedule s "
                                + "where s.groupRoutine.title = :value",
                        routineTitle
                )).isEqualTo(1);
                assertThat(count(
                        "select count(a) from GroupRoutineAssignment a "
                                + "where a.member.id = :value",
                        memberId
                )).isEqualTo(1);
            });
        } finally {
            transaction.executeWithoutResult(status -> cleanup(memberId, collisionGroupId));
        }
    }

    private long count(String jpql, Object value) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("value", value)
                .getSingleResult();
    }

    private void cleanup(Long memberId, Long collisionGroupId) {
        entityManager.createNativeQuery(
                        "delete from group_routine_assignment where member_id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        delete schedule from group_routine_schedule schedule
                        join group_routine routine on routine.id = schedule.group_routine_id
                        join group_member membership on membership.group_id = routine.group_id
                        where membership.member_id = :memberId
                        """)
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        delete routine from group_routine routine
                        join group_member membership on membership.group_id = routine.group_id
                        where membership.member_id = :memberId
                        """)
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        delete category from group_routine_category category
                        join group_member membership on membership.group_id = category.group_id
                        where membership.member_id = :memberId
                        """)
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "delete from group_member where member_id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery(
                        "delete from member_group where invite_code = :successCode or id = :collisionId")
                .setParameter("successCode", SUCCESS_CODE)
                .setParameter("collisionId", collisionGroupId)
                .executeUpdate();
        deleteDependencies(entityManager, memberId);
        entityManager.createNativeQuery("delete from member where id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
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
                        "재시도 후 한 세트만 저장합니다.",
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
                .email("group-create-retry-" + suffix + "@example.com")
                .nickname("재시도회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-create-retry-" + suffix)
                .build();
    }
}
