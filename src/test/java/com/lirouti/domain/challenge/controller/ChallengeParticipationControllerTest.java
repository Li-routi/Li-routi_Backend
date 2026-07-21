package com.lirouti.domain.challenge.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.auth.CustomUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("ChallengeParticipationController MockMvc 테스트")
class ChallengeParticipationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    private Member persistMember() {
        Member m = Member.builder()
                .email("partmvc@ex.com").nickname("partmvc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("partmvc-sid").build();
        em.persist(m);
        return m;
    }

    private Challenge persistChallenge() {
        Challenge c = Challenge.builder()
                .name("part챌린지").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private CustomUserDetails principal(Member m) {
        return new CustomUserDetails(m.getId(), Role.ROLE_USER);
    }

    @Test
    @DisplayName("인증 없이 참여 요청하면 거부된다(403)")
    void participate_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(post("/api/challenges/{id}/participation", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("참여하면 200과 participating=true, 회차 1을 돌려준다")
    void participate_Success() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        em.flush();

        mockMvc.perform(post("/api/challenges/{id}/participation", c.getId()).with(user(principal(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.participating").value(true))
                .andExpect(jsonPath("$.result.participationRound").value(1))
                .andExpect(jsonPath("$.result.challengeId").value(c.getId()));
    }

    @Test
    @DisplayName("이미 참여 중이면 409를 반환한다")
    void participate_AlreadyParticipating_Returns409() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        em.flush();

        mockMvc.perform(post("/api/challenges/{id}/participation", c.getId()).with(user(principal(me))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/challenges/{id}/participation", c.getId()).with(user(principal(me))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE409_1"));
    }

    @Test
    @DisplayName("참여 후 이탈하면 200과 participating=false를 돌려준다")
    void leave_Success() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        em.flush();

        mockMvc.perform(post("/api/challenges/{id}/participation", c.getId()).with(user(principal(me))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/challenges/{id}/participation", c.getId()).with(user(principal(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.participating").value(false));
    }

    @Test
    @DisplayName("참여 중이 아닌 챌린지를 이탈하면 409를 반환한다")
    void leave_NotParticipating_Returns409() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        em.flush();

        mockMvc.perform(delete("/api/challenges/{id}/participation", c.getId()).with(user(principal(me))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE409_2"));
    }
}
