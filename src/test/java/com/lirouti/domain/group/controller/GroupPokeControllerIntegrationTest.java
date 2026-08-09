package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("그룹 구성원 찌르기 API 통합 테스트")
class GroupPokeControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager entityManager;

    @Test
    @DisplayName("ACTIVE 구성원이 다른 ACTIVE 구성원을 반복해 찌르면 매번 누적값이 증가한다")
    void poke_ActiveMembers_IncrementsAndReturnsUpdatedCount() throws Exception {
        Group group = group();
        Member requester = member("요청자");
        Member target = member("대상");
        membership(group, requester);
        GroupMember targetMembership = membership(group, target);
        entityManager.flush();

        for (int expectedCount = 1; expectedCount <= 2; expectedCount++) {
            mockMvc.perform(post("/api/groups/{groupId}/members/{targetMemberId}/pokes",
                            group.getId(), target.getId())
                            .with(user(principal(requester))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("GROUP200_15"))
                    .andExpect(jsonPath("$.result.memberId").value(target.getId()))
                    .andExpect(jsonPath("$.result.totalPokeCount").value(expectedCount));
        }

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(GroupMember.class, targetMembership.getId()).getTotalPokeCount())
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("그룹 상세 구성원 응답에 누적 찔림 수를 포함한다")
    void getGroupDetail_IncludesTotalPokeCount() throws Exception {
        Group group = group();
        Member requester = member("요청자");
        Member target = member("대상");
        membership(group, requester);
        GroupMember targetMembership = membership(group, target);
        targetMembership.increaseTotalPokeCount();
        targetMembership.increaseTotalPokeCount();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/groups/{groupId}", group.getId())
                        .with(user(principal(requester))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.members[1].memberId").value(target.getId()))
                .andExpect(jsonPath("$.result.members[1].totalPokeCount").value(2));
    }

    @Test
    @DisplayName("자기 자신 또는 비ACTIVE 대상을 찌를 수 없다")
    void poke_SelfOrInactiveTarget_ReturnsDefinedErrors() throws Exception {
        Group group = group();
        Member requester = member("요청자");
        Member target = member("대상");
        membership(group, requester);
        GroupMember targetMembership = membership(group, target);
        targetMembership.leave();
        entityManager.flush();

        mockMvc.perform(post("/api/groups/{groupId}/members/{targetMemberId}/pokes",
                        group.getId(), requester.getId())
                        .with(user(principal(requester))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP400_2"));
        mockMvc.perform(post("/api/groups/{groupId}/members/{targetMemberId}/pokes",
                        group.getId(), target.getId())
                        .with(user(principal(requester))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP404_5"));
    }

    @Test
    @DisplayName("비ACTIVE 요청자는 찌르기 요청을 할 수 없다")
    void poke_InactiveRequester_ReturnsAccessDenied() throws Exception {
        Group group = group();
        Member requester = member("요청자");
        Member target = member("대상");
        GroupMember requesterMembership = membership(group, requester);
        membership(group, target);
        requesterMembership.leave();
        entityManager.flush();

        mockMvc.perform(post("/api/groups/{groupId}/members/{targetMemberId}/pokes",
                        group.getId(), target.getId())
                        .with(user(principal(requester))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_2"));
    }

    @Test
    @DisplayName("존재하지 않거나 DELETED 상태인 그룹에는 기존 그룹 예외 코드를 반환한다")
    void poke_MissingOrDeletedGroup_ReturnsExistingGroupErrors() throws Exception {
        Group group = group();
        Member requester = member("요청자");
        Member target = member("대상");
        membership(group, requester);
        membership(group, target);
        entityManager.flush();

        mockMvc.perform(post("/api/groups/{groupId}/members/{targetMemberId}/pokes",
                        Long.MAX_VALUE, target.getId())
                        .with(user(principal(requester))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP404_1"));

        group.delete();
        entityManager.flush();
        mockMvc.perform(post("/api/groups/{groupId}/members/{targetMemberId}/pokes",
                        group.getId(), target.getId())
                        .with(user(principal(requester))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_1"));
    }

    private Group group() {
        Group group = Group.builder().name("찌르기 그룹")
                .inviteCode(UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase())
                .build();
        entityManager.persist(group);
        return group;
    }

    private Member member(String nickname) {
        String identifier = UUID.randomUUID().toString();
        Member member = Member.builder().email(identifier + "@example.com").nickname(nickname)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER).socialId(identifier).build();
        entityManager.persist(member);
        return member;
    }

    private GroupMember membership(Group group, Member member) {
        GroupMember membership = GroupMember.builder()
                .group(group).member(member).role(GroupMemberRole.MEMBER).build();
        entityManager.persist(membership);
        return membership;
    }

    private CustomUserDetails principal(Member member) {
        return new CustomUserDetails(member.getId(), Role.ROLE_USER);
    }
}
