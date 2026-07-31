package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("그룹 카테고리 API 통합 테스트")
class GroupCategoryControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager em;

    @Test
    @DisplayName("ACTIVE 구성원은 기본 6개와 해당 그룹의 활성 카테고리만 정렬된 순서로 조회한다")
    void getCategories_ActiveMember_ReturnsFixedAndOwnActiveOnly() throws Exception {
        // given
        Group target = group();
        Group other = group();
        Member member = member();
        membership(target, member, GroupMemberRole.MEMBER);
        category(target, "아침 관리", true);
        category(target, "비활성 관리", false);
        category(other, "다른 그룹 관리", true);
        em.flush();
        em.clear();

        // when & then
        mockMvc.perform(get("/api/groups/{groupId}/categories", target.getId())
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GROUP200_4"))
                .andExpect(jsonPath("$.result.categories.length()").value(7))
                .andExpect(jsonPath("$.result.categories[*].name", contains(
                        "운동", "건강", "자기계발", "생활정리", "마음관리", "취미", "아침 관리")))
                .andExpect(jsonPath("$.result.categories[0].fixed").value(true))
                .andExpect(jsonPath("$.result.categories[6].fixed").value(false))
                .andExpect(jsonPath("$.result.addableCount").value(4));
    }

    @Test
    @DisplayName("비구성원과 비활성 구성원은 그룹 카테고리를 조회할 수 없다")
    void getCategories_NonMemberOrInactiveMember_ReturnsForbidden() throws Exception {
        // given
        Group group = group();
        Member nonMember = member();
        Member inactiveMember = member();
        GroupMember inactiveMembership = membership(group, inactiveMember, GroupMemberRole.MEMBER);
        inactiveMembership.leave();
        em.flush();

        // when & then
        for (Member requester : new Member[]{nonMember, inactiveMember}) {
            mockMvc.perform(get("/api/groups/{groupId}/categories", group.getId())
                            .with(user(principal(requester))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GROUP403_2"));
        }
    }

    @Test
    @DisplayName("ACTIVE OWNER는 trim된 이름과 선택 색상으로 카테고리를 생성한다")
    void createCategory_ActiveOwner_ReturnsCreatedCategory() throws Exception {
        // given
        Group group = group();
        Member owner = member();
        membership(group, owner, GroupMemberRole.OWNER);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/categories", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  아침 관리  ","color":"BLUE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("GROUP201_4"))
                .andExpect(jsonPath("$.result.categoryId").isNumber())
                .andExpect(jsonPath("$.result.name").value("아침 관리"))
                .andExpect(jsonPath("$.result.color").value("BLUE"))
                .andExpect(jsonPath("$.result.fixed").value(false));
    }

    @Test
    @DisplayName("일반 MEMBER는 그룹 카테고리를 생성할 수 없다")
    void createCategory_RegularMember_ReturnsOwnerDenied() throws Exception {
        // given
        Group group = group();
        Member member = member();
        membership(group, member, GroupMemberRole.MEMBER);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/categories", group.getId())
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"관리\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_3"));
    }

    @Test
    @DisplayName("빈 이름, 길이 초과와 줄바꿈은 공통 요청 검증 오류를 반환한다")
    void createCategory_InvalidName_ReturnsValidationError() throws Exception {
        // given
        Group group = group();
        Member owner = member();
        membership(group, owner, GroupMemberRole.OWNER);
        em.flush();

        // when & then
        for (String body : new String[]{
                "{\"name\":\"   \"}",
                "{\"name\":\"12345678901\"}",
                "{\"name\":\"두\\n줄\"}"
        }) {
            mockMvc.perform(post("/api/groups/{groupId}/categories", group.getId())
                            .with(user(principal(owner)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON400_1"));
        }
    }

    @Test
    @DisplayName("기본 및 같은 그룹이 사용한 활성·비활성 카테고리 이름은 중복 생성할 수 없다")
    void createCategory_DuplicateFixedOrGroupName_ReturnsConflict() throws Exception {
        // given
        Group group = group();
        Member owner = member();
        membership(group, owner, GroupMemberRole.OWNER);
        category(group, "아침 관리", true);
        category(group, "비활성 관리", false);
        em.flush();

        // when & then
        for (String name : new String[]{"운동", "아침 관리", "비활성 관리"}) {
            mockMvc.perform(post("/api/groups/{groupId}/categories", group.getId())
                            .with(user(principal(owner)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GROUP409_10"));
        }
    }

    @Test
    @DisplayName("활성 사용자 카테고리가 5개인 그룹은 더 추가할 수 없다")
    void createCategory_AtLimit_ReturnsConflict() throws Exception {
        // given
        Group group = group();
        Member owner = member();
        membership(group, owner, GroupMemberRole.OWNER);
        for (int index = 0; index < 5; index++) {
            category(group, "관리" + index, true);
        }
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/categories", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"여섯번째\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP409_9"));
    }

    @Test
    @DisplayName("OpenAPI 문서에 그룹 카테고리 조회와 추가 경로가 노출된다")
    void openApi_GroupCategoryApis_AreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/api/groups/{groupId}/categories")))
                .andExpect(content().string(containsString("그룹 루틴 카테고리 목록 조회")))
                .andExpect(content().string(containsString("그룹 루틴 카테고리 추가")));
    }

    private Group group() {
        Group group = Group.builder()
                .name("카테고리 그룹")
                .inviteCode(UUID.randomUUID().toString().substring(0, 7).toUpperCase())
                .build();
        em.persist(group);
        return group;
    }

    private Member member() {
        String suffix = UUID.randomUUID().toString();
        Member member = Member.builder()
                .email(suffix + "@example.com")
                .nickname("카테고리회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId(suffix)
                .build();
        em.persist(member);
        return member;
    }

    private GroupMember membership(Group group, Member member, GroupMemberRole role) {
        GroupMember membership = GroupMember.builder()
                .group(group).member(member).role(role).build();
        em.persist(membership);
        return membership;
    }

    private GroupRoutineCategory category(Group group, String name, boolean active) {
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .group(group).name(name).active(active).build();
        em.persist(category);
        return category;
    }

    private CustomUserDetails principal(Member member) {
        return new CustomUserDetails(member.getId(), Role.ROLE_USER);
    }
}
