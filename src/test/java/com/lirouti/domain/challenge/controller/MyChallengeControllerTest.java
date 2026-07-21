package com.lirouti.domain.challenge.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
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
@DisplayName("MyChallengeController MockMvc 테스트")
class MyChallengeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    private Member persistMember() {
        Member m = Member.builder()
                .email("mymvc@ex.com").nickname("mymvc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mymvc-sid").build();
        em.persist(m);
        return m;
    }

    private CustomUserDetails principal(Member m) {
        return new CustomUserDetails(m.getId(), Role.ROLE_USER);
    }

    @Test
    @DisplayName("인증이 없으면 접근이 거부된다(403)")
    void getMyChallenges_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(get("/api/members/me/challenges"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 회원의 활성 참여 챌린지를 심플 카드로 돌려준다")
    void getMyChallenges_ReturnsMyActiveChallenges() throws Exception {
        Member me = persistMember();
        Challenge joined = Challenge.builder()
                .name("mymvc참여").description("d").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(joined);
        em.persist(MemberChallenge.builder()
                .member(me).challenge(joined)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());
        em.flush();

        mockMvc.perform(get("/api/members/me/challenges").with(user(principal(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.challenges").isArray())
                .andExpect(jsonPath("$.result.challenges[0].challengeId").value(joined.getId()))
                .andExpect(jsonPath("$.result.challenges[0].category").value("HEALTH"));
    }
}
