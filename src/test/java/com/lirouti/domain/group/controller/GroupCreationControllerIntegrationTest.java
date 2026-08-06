package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("모임방 통합 생성 Controller 통합 테스트")
class GroupCreationControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("HTTP 요청이 실제 서비스까지 연결되어 그룹 전체와 오늘 OWNER 할당을 생성한다")
    void createGroup_ValidRequest_PersistsAggregateAndReturnsCreated() throws Exception {
        // given
        String suffix = suffix();
        String groupName = "API모임" + suffix;
        String categoryName = "분류" + suffix.substring(2);
        String routineTitle = "루틴" + suffix.substring(1);
        Long memberId = saveMember(suffix);

        try {
            // when & then
            mockMvc.perform(post("/api/groups")
                            .with(user(principal(memberId)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request(groupName, categoryName, routineTitle)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.code").value("GROUP201_3"))
                    .andExpect(jsonPath("$.result.name").value(groupName))
                    .andExpect(jsonPath("$.result.customCategories.length()").value(1))
                    .andExpect(jsonPath("$.result.routines.length()").value(1))
                    .andExpect(jsonPath("$.result.assignmentCount").value(1))
                    .andExpect(jsonPath("$.result.inviteCode").doesNotExist())
                    .andExpect(jsonPath("$.result.inviteCodeExpiresAt").doesNotExist());

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(count("select count(g) from Group g where g.name = :value", groupName))
                        .isEqualTo(1);
                Group createdGroup = entityManager.createQuery(
                                "select g from Group g where g.name = :name", Group.class
                        ).setParameter("name", groupName)
                        .getSingleResult();
                assertThat(createdGroup.getInviteCode()).isNotBlank();
                assertThat(createdGroup.isLocked()).isFalse();
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
                        "select count(a) from GroupRoutineAssignment a where a.member.id = :value",
                        memberId
                )).isEqualTo(1);
            });
        } finally {
            cleanup(memberId, groupName);
        }
    }

    @Test
    @DisplayName("활성 그룹 6개에 참여한 회원의 생성 요청은 API에서 그룹 오류로 반환한다")
    void createGroup_ActiveGroupLimitReached_ReturnsGroupConflict() throws Exception {
        // given
        String suffix = suffix();
        String requestedGroupName = "초과모임" + suffix;
        Long memberId = saveMember(suffix);
        saveSixActiveGroups(memberId, suffix);

        try {
            // when & then
            mockMvc.perform(post("/api/groups")
                            .with(user(principal(memberId)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fixedCategoryRequest(requestedGroupName)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("GROUP409_6"))
                    .andExpect(jsonPath("$.result").doesNotExist());

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(count(
                        "select count(gm) from GroupMember gm where gm.member.id = :value",
                        memberId
                )).isEqualTo(6);
                assertThat(count(
                        "select count(g) from Group g where g.name = :value",
                        requestedGroupName
                )).isZero();
            });
        } finally {
            cleanup(memberId, requestedGroupName);
        }
    }

    @Test
    @DisplayName("OpenAPI 문서에 모임방 통합 생성 경로와 201 응답이 노출된다")
    void openApi_GroupCreation_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/groups")))
                .andExpect(content().string(containsString("모임방 통합 생성")))
                .andExpect(content().string(containsString("201")));
    }

    private Long saveMember(String suffix) {
        return new TransactionTemplate(transactionManager).execute(status ->
                memberRepository.saveAndFlush(Member.builder()
                        .email("group-api-" + suffix + "@example.com")
                        .nickname("API회원")
                        .socialProvider(SocialProvider.GOOGLE)
                        .role(Role.ROLE_USER)
                        .socialId("group-api-" + suffix)
                        .build()).getId()
        );
    }

    private void saveSixActiveGroups(Long memberId, String suffix) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Member member = memberRepository.findById(memberId).orElseThrow();
            for (int index = 0; index < 6; index++) {
                Group group = groupRepository.saveAndFlush(Group.builder()
                        .name("기존모임" + index)
                        .inviteCode("T" + suffix.substring(0, 4) + index + "X")
                        .build());
                groupMemberRepository.saveAndFlush(GroupMember.builder()
                        .member(member)
                        .group(group)
                        .role(index == 0 ? GroupMemberRole.OWNER : GroupMemberRole.MEMBER)
                        .build());
            }
        });
    }

    private long count(String jpql, Object value) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("value", value)
                .getSingleResult();
    }

    private void cleanup(Long memberId, String requestedGroupName) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Set<Long> groupIds = new LinkedHashSet<>(entityManager.createQuery(
                            "select gm.group.id from GroupMember gm where gm.member.id = :memberId",
                            Long.class
                    ).setParameter("memberId", memberId)
                    .getResultList());
            groupIds.addAll(entityManager.createQuery(
                            "select g.id from Group g where g.name = :name", Long.class
                    ).setParameter("name", requestedGroupName)
                    .getResultList());

            for (Long groupId : groupIds) {
                delete("delete from group_routine_assignment where group_routine_id in "
                        + "(select id from group_routine where group_id = :groupId)", groupId);
                delete("delete from group_routine_schedule where group_routine_id in "
                        + "(select id from group_routine where group_id = :groupId)", groupId);
                delete("delete from group_routine where group_id = :groupId", groupId);
                delete("delete from group_routine_category where group_id = :groupId", groupId);
                delete("delete from group_member where group_id = :groupId", groupId);
                delete("delete from member_group where id = :groupId", groupId);
            }
            entityManager.createNativeQuery("delete from member where id = :memberId")
                    .setParameter("memberId", memberId)
                    .executeUpdate();
        });
    }

    private void delete(String sql, Long groupId) {
        entityManager.createNativeQuery(sql)
                .setParameter("groupId", groupId)
                .executeUpdate();
    }

    private CustomUserDetails principal(Long memberId) {
        return new CustomUserDetails(memberId, Role.ROLE_USER);
    }

    private String request(String groupName, String categoryName, String routineTitle) {
        return """
                {
                  "name": "%s",
                  "customCategories": [
                    {"clientKey": "custom", "name": "%s", "color": "BLUE"}
                  ],
                  "routines": [
                    {"categoryKey": "custom", "title": "%s", "description": "API 통합 테스트",
                     "schedules": [{"repeatDay": "%s", "startTime": "09:00", "endTime": "10:00"}]}
                  ]
                }
                """.formatted(
                groupName,
                categoryName,
                routineTitle,
                LocalDate.now(ZoneId.of("Asia/Seoul")).getDayOfWeek().name()
        );
    }

    private String fixedCategoryRequest(String groupName) {
        return """
                {
                  "name": "%s",
                  "customCategories": [],
                  "routines": [
                    {"categoryId": 1, "title": "초과 루틴", "description": "상한 검증",
                     "schedules": [{"repeatDay": "MONDAY", "startTime": "09:00", "endTime": "10:00"}]}
                  ]
                }
                """.formatted(groupName);
    }

    private String suffix() {
        String value = Long.toString(System.nanoTime());
        return value.substring(value.length() - 6);
    }
}
