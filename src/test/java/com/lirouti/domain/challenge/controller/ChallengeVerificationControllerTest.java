package com.lirouti.domain.challenge.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
@DisplayName("ChallengeVerificationController MockMvc 테스트")
class ChallengeVerificationControllerTest {

    private static final String VALID_KEY =
            "challenge-verifications/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    private Member persistMember() {
        Member m = Member.builder()
                .email("vermvc@ex.com").nickname("vermvc")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("vermvc-sid").build();
        em.persist(m);
        return m;
    }

    private Challenge persistChallenge() {
        Challenge c = Challenge.builder()
                .name("ver챌린지").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private void persistParticipation(Member m, Challenge c) {
        em.persist(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());
    }

    private CustomUserDetails principal(Member m) {
        return new CustomUserDetails(m.getId(), Role.ROLE_USER);
    }

    private String body(String mediaKey, String content) {
        return """
                {"mediaKey": "%s", "content": "%s"}
                """.formatted(mediaKey, content);
    }

    @Test
    @DisplayName("인증 없이 인증하기를 요청하면 거부된다(403)")
    void verify_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(post("/api/challenges/{id}/verifications", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_KEY, "hi")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("피드 조회도 로그인이 필요하다(403) — 목록·상세와 달리 공개 경로가 아니다")
    void getFeed_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(get("/api/challenges/{id}/verifications", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증하면 200과 스트릭 1, reverified=false를 돌려준다")
    void verify_Success() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        persistParticipation(me, c);
        em.flush();

        mockMvc.perform(post("/api/challenges/{id}/verifications", c.getId())
                        .with(user(principal(me)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_KEY, "오늘도 완료")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.currentStreak").value(1))
                .andExpect(jsonPath("$.result.reverified").value(false))
                .andExpect(jsonPath("$.result.content").value("오늘도 완료"))
                // 응답에는 저장된 key가 아니라 조립된 공개 URL이 나간다.
                .andExpect(jsonPath("$.result.imageUrl").value(endsWith(VALID_KEY)))
                .andExpect(jsonPath("$.result.imageUrl").value(startsWith("http")));
    }

    @Test
    @DisplayName("mediaKey가 비어 있으면 400")
    void verify_BlankMediaKey_IsBadRequest() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        persistParticipation(me, c);
        em.flush();

        mockMvc.perform(post("/api/challenges/{id}/verifications", c.getId())
                        .with(user(principal(me)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "코멘트")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("참여 중이 아닌 챌린지에 인증하면 409")
    void verify_NotParticipating_IsConflict() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        em.flush();

        mockMvc.perform(post("/api/challenges/{id}/verifications", c.getId())
                        .with(user(principal(me)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_KEY, "코멘트")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("피드 조회는 200과 닉네임·코멘트가 담긴 카드를 돌려준다")
    void getFeed_Success() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        persistParticipation(me, c);
        em.flush();

        mockMvc.perform(post("/api/challenges/{id}/verifications", c.getId())
                .with(user(principal(me)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(VALID_KEY, "피드에 보일 코멘트")));

        mockMvc.perform(get("/api/challenges/{id}/verifications", c.getId())
                        .with(user(principal(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.verifications[0].nickname").value("vermvc"))
                .andExpect(jsonPath("$.result.verifications[0].content").value("피드에 보일 코멘트"))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("없는 챌린지의 피드를 조회하면 404")
    void getFeed_UnknownChallenge_IsNotFound() throws Exception {
        Member me = persistMember();
        em.flush();

        mockMvc.perform(get("/api/challenges/{id}/verifications", 999_999_999L)
                        .with(user(principal(me))))
                .andExpect(status().isNotFound());
    }
}
