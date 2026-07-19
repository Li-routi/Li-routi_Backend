package com.lirouti.domain.challenge.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("ChallengeController MockMvc 테스트")
class ChallengeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    private Challenge persistChallenge() {
        Challenge c = Challenge.builder()
                .name("물 1L 마시기").description("설명")
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        em.flush();
        return c;
    }

    @Test
    @DisplayName("목록 조회는 로그인 없이 200, 응답은 래퍼 객체(challenges·totalCount)다")
    void getChallenges_Public_ReturnsWrappedList() throws Exception {
        mockMvc.perform(get("/api/challenges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.challenges").isArray())
                .andExpect(jsonPath("$.result.totalCount").exists());
    }

    @Test
    @DisplayName("잘못된 sort 값은 400을 반환한다")
    void getChallenges_InvalidSort_Returns400() throws Exception {
        mockMvc.perform(get("/api/challenges").param("sort", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("잘못된 category 값은 400을 반환한다")
    void getChallenges_InvalidCategory_Returns400() throws Exception {
        mockMvc.perform(get("/api/challenges").param("category", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하는 챌린지 상세는 200과 카운트를 반환한다")
    void getChallenge_Existing_Returns200() throws Exception {
        Challenge c = persistChallenge();

        mockMvc.perform(get("/api/challenges/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.challengeId").value(c.getId()))
                .andExpect(jsonPath("$.result.name").value("물 1L 마시기"))
                .andExpect(jsonPath("$.result.participantCount").value(0))
                .andExpect(jsonPath("$.result.todayCompletionCount").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 챌린지 상세는 404를 반환한다")
    void getChallenge_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/challenges/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHALLENGE404_1"));
    }

    @Test
    @DisplayName("숫자가 아닌 challengeId는 400을 반환한다")
    void getChallenge_MalformedId_Returns400() throws Exception {
        mockMvc.perform(get("/api/challenges/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST는 인증이 필요하다 (GET만 공개) — 401/403")
    void postChallenges_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(post("/api/challenges"))
                .andExpect(status().is4xxClientError());
    }
}
