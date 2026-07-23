package com.lirouti.domain.group.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("GroupController 그룹 루틴 생성 통합 테스트")
class GroupControllerTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("OWNER가 생성하면 201과 저장 결과 및 전체 구성원 할당 수를 반환한다")
    void createRoutine_Owner_ReturnsCreatedResult() throws Exception {
        // given
        Group group = group("GC00001");
        Member owner = member();
        Member member = member();
        membership(owner, group, GroupMemberRole.OWNER);
        membership(member, group, GroupMemberRole.MEMBER);
        RoutineCategory category = category(true);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(category.getId(), "공동 정리")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP201_1"))
                .andExpect(jsonPath("$.result.groupId").value(group.getId()))
                .andExpect(jsonPath("$.result.categoryId").value(category.getId()))
                .andExpect(jsonPath("$.result.categoryName").value(category.getName()))
                .andExpect(jsonPath("$.result.title").value("공동 정리"))
                .andExpect(jsonPath("$.result.assignmentCount").value(2))
                .andExpect(jsonPath("$.result.schedules[0].repeatDay").value("MONDAY"))
                .andExpect(jsonPath("$.result.schedules[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.result.schedules[1].repeatDay").value("FRIDAY"));

        em.flush();
        em.clear();
        Long routineCount = em.createQuery(
                        "select count(gr) from GroupRoutine gr where gr.group.id = :groupId",
                        Long.class
                ).setParameter("groupId", group.getId())
                .getSingleResult();
        Long assignmentCount = em.createQuery(
                        "select count(a) from GroupRoutineAssignment a "
                                + "where a.groupRoutine.group.id = :groupId",
                        Long.class
                ).setParameter("groupId", group.getId())
                .getSingleResult();
        org.assertj.core.api.Assertions.assertThat(routineCount).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(assignmentCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("OWNER 한 명만 있는 그룹도 할당 한 건으로 생성한다")
    void createRoutine_OwnerOnly_ReturnsOneAssignment() throws Exception {
        // given
        Group group = group("GC00002");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        RoutineCategory category = category(true);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(category.getId(), "단독 루틴")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.assignmentCount").value(1));
    }

    @Test
    @DisplayName("일반 구성원은 그룹 루틴을 생성할 수 없다")
    void createRoutine_RegularMember_ReturnsOwnerAccessDenied() throws Exception {
        // given
        Group group = group("GC00003");
        Member regularMember = member();
        membership(regularMember, group, GroupMemberRole.MEMBER);
        RoutineCategory category = category(true);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(regularMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(category.getId(), "권한 없는 루틴")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_3"));
    }

    @Test
    @DisplayName("동일 그룹의 같은 제목은 409를 반환한다")
    void createRoutine_DuplicateTitle_ReturnsConflict() throws Exception {
        // given
        Group group = group("GC00004");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        RoutineCategory category = category(true);
        em.flush();
        String request = validRequest(category.getId(), "중복 루틴");
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP409_3"));
    }

    @Test
    @DisplayName("같은 요일이 중복되거나 시간 범위가 잘못되면 400을 반환한다")
    void createRoutine_InvalidSchedules_ReturnsBadRequest() throws Exception {
        // given
        Group group = group("GC00005");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        RoutineCategory category = category(true);
        em.flush();
        String request = """
                {
                  "categoryId": %d,
                  "title": "잘못된 일정",
                  "description": "요일과 시간 검증",
                  "schedules": [
                    {"repeatDay": "MONDAY", "startTime": "10:00", "endTime": "09:00"},
                    {"repeatDay": "MONDAY", "startTime": "18:00", "endTime": "19:00"}
                  ]
                }
                """.formatted(category.getId());

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("인증 없이 그룹 루틴 생성 요청을 하면 거부된다")
    void createRoutine_Unauthenticated_ReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/routines", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OpenAPI 문서에 그룹 루틴 생성 경로와 201 응답이 노출된다")
    void openApi_GroupRoutineCreation_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/groups/{groupId}/routines")))
                .andExpect(content().string(containsString("그룹 루틴 생성")))
                .andExpect(content().string(containsString("201")));
    }

    private String validRequest(Long categoryId, String title) {
        return """
                {
                  "categoryId": %d,
                  "title": "%s",
                  "description": "그룹 루틴 설명입니다.",
                  "schedules": [
                    {"repeatDay": "FRIDAY", "startTime": "20:00", "endTime": "21:00"},
                    {"repeatDay": "MONDAY", "startTime": "09:00", "endTime": "10:00"}
                  ]
                }
                """.formatted(categoryId, title);
    }

    private Group group(String inviteCode) {
        Group group = Group.builder().name("컨트롤러 그룹").inviteCode(inviteCode).build();
        em.persist(group);
        return group;
    }

    private Member member() {
        int value = sequence.incrementAndGet();
        Member member = Member.builder()
                .email("group-controller-" + value + "@example.com")
                .nickname("컨트롤러회원" + value)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-controller-social-" + value)
                .build();
        em.persist(member);
        return member;
    }

    private GroupMember membership(Member member, Group group, GroupMemberRole role) {
        GroupMember membership = GroupMember.builder()
                .member(member)
                .group(group)
                .role(role)
                .build();
        em.persist(membership);
        return membership;
    }

    private RoutineCategory category(boolean active) {
        int value = sequence.incrementAndGet();
        RoutineCategory category = RoutineCategory.builder()
                .name("컨트롤러 카테고리" + value)
                .active(active)
                .build();
        em.persist(category);
        return category;
    }

    private CustomUserDetails principal(Member member) {
        return new CustomUserDetails(member.getId(), Role.ROLE_USER);
    }
}
