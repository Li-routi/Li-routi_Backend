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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

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
                .name("물 1L 마시기").description("설명").imageUrl("https://img/water.png")
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        em.flush();
        return c;
    }

    // 한 회원이 챌린지에 참여하고 오늘 인증까지 마친 상태를 심는다.
    // 상세 집계(참여자 수·오늘 완료자 수)가 0이 아닌 경로를 컨트롤러→서비스→리포지토리 끝까지 태우기 위함.
    private void persistParticipantWithTodayVerification(Challenge c) {
        Member m = Member.builder()
                .email("mvc-detail@ex.com").nickname("mvcDetail")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mvc-detail-sid").build();
        em.persist(m);

        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);

        // 서비스가 오늘 완료자 수를 KST 기준으로 판단하므로, 인증일도 KST 오늘로 맞춘다
        // (테스트 머신 타임존이 UTC여도 자정 근처에서 어긋나지 않게).
        LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));
        em.persist(ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(todayKst).verifiedAt(LocalDateTime.now())
                .imageUrl("https://img/proof.png").build());
        em.flush();
    }

    @Test
    @DisplayName("목록 조회는 로그인 없이 200, 응답은 무한스크롤 래퍼(challenges·nextCursor·hasNext)다")
    void getChallenges_Public_ReturnsCursorList() throws Exception {
        mockMvc.perform(get("/api/challenges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.challenges").isArray())
                .andExpect(jsonPath("$.result.hasNext").exists());
    }

    @Test
    @DisplayName("커서 페이징: size=1로 두 건을 스크롤하면 nextCursor로 이어지고 마지막엔 hasNext=false")
    void getChallenges_CursorPaging() throws Exception {
        Challenge older = Challenge.builder()
                .name("mvctag오래된").description("d").category(ChallengeCategory.HOBBY).active(true).build();
        Challenge newer = Challenge.builder()
                .name("mvctag최신").description("d").category(ChallengeCategory.HOBBY).active(true).build();
        em.persist(older);
        em.persist(newer);
        em.flush();

        // 첫 페이지: 최신(newer)이 먼저, hasNext=true, nextCursor=newer.id, 카드에 통계·루틴 주기 포함
        mockMvc.perform(get("/api/challenges").param("keyword", "mvctag").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.challenges.length()").value(1))
                .andExpect(jsonPath("$.result.challenges[0].challengeId").value(newer.getId()))
                .andExpect(jsonPath("$.result.challenges[0].routineCycle").value("DAILY"))
                .andExpect(jsonPath("$.result.challenges[0].participantCount").value(0))
                .andExpect(jsonPath("$.result.challenges[0].verificationPostCount").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.nextCursor").value(newer.getId()));

        // 다음 페이지: cursor=newer.id → older 한 건, hasNext=false, nextCursor=null
        mockMvc.perform(get("/api/challenges")
                        .param("keyword", "mvctag").param("size", "1")
                        .param("cursor", String.valueOf(newer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.challenges.length()").value(1))
                .andExpect(jsonPath("$.result.challenges[0].challengeId").value(older.getId()))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("잘못된 category 값은 400을 반환한다")
    void getChallenges_InvalidCategory_Returns400() throws Exception {
        mockMvc.perform(get("/api/challenges").param("category", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하는 챌린지 상세는 200과 참여자 수·오늘 완료자 수(비-0)를 끝까지 태워 반환한다")
    void getChallenge_Existing_Returns200() throws Exception {
        Challenge c = persistChallenge();
        persistParticipantWithTodayVerification(c);

        mockMvc.perform(get("/api/challenges/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.challengeId").value(c.getId()))
                .andExpect(jsonPath("$.result.name").value("물 1L 마시기"))
                .andExpect(jsonPath("$.result.imageUrl").value("https://img/water.png"))
                .andExpect(jsonPath("$.result.routineCycle").value("DAILY"))
                .andExpect(jsonPath("$.result.participantCount").value(1))
                .andExpect(jsonPath("$.result.todayCompletionCount").value(1));
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
    @DisplayName("POST는 인증이 필요하다 (GET만 공개) — 미인증은 403")
    void postChallenges_Unauthenticated_IsRejected() throws Exception {
        // SecurityConfig가 GET만 permitAll하고 나머지는 authenticated. 커스텀 EntryPoint를 안 붙여
        // Spring Security 기본 Http403ForbiddenEntryPoint가 미인증 요청에 403을 낸다.
        mockMvc.perform(post("/api/challenges"))
                .andExpect(status().isForbidden());
    }
}
