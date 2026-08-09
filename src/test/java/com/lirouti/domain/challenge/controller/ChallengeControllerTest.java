package com.lirouti.domain.challenge.controller;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("ChallengeController MockMvc 테스트")
class ChallengeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    /**
     * 조회용 로그인 회원. 챌린지 조회 API가 인증을 요구하므로(#77) 모든 조회 호출에 붙인다.
     * 참여는 하지 않은 상태라 participating은 false로 나온다.
     */
    private CustomUserDetails viewer;

    @BeforeEach
    void setUpViewer() {
        Member m = Member.builder()
                .email("mvc-viewer@ex.com").nickname("mvcViewer")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mvc-viewer-sid").build();
        em.persist(m);
        em.flush();
        viewer = new CustomUserDetails(m.getId(), Role.ROLE_USER);
    }

    private Challenge persistChallenge() {
        Challenge c = Challenge.builder()
                .name("물 1L 마시기").description("설명").imageUrl("https://img/water.png")
                .category(ChallengeCategory.HEALTH).reward(30).active(true).build();
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
                .verifiedDate(todayKst)
                .periodStartDate(todayKst).verifiedAt(LocalDateTime.now())
                .imageUrl("https://img/proof.png").build());
        em.flush();
    }

    @Test
    @DisplayName("목록 조회 응답은 무한스크롤 래퍼(challenges·nextCursor·hasNext)다")
    void getChallenges_ReturnsCursorList() throws Exception {
        mockMvc.perform(get("/api/challenges").with(user(viewer)))
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
        // reward를 주지 않으면 엔티티 빌더가 0으로 채운다. 목록 카드에 그대로 실리는지 함께 본다.
        Challenge newer = Challenge.builder()
                .name("mvctag최신").description("d").category(ChallengeCategory.HOBBY).reward(15).active(true).build();
        em.persist(older);
        em.persist(newer);
        em.flush();

        // 첫 페이지: 최신(newer)이 먼저, hasNext=true, nextCursor=newer.id, 카드에 통계·루틴 주기 포함
        mockMvc.perform(get("/api/challenges").with(user(viewer))
                        .param("keyword", "mvctag").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.challenges.length()").value(1))
                .andExpect(jsonPath("$.result.challenges[0].challengeId").value(newer.getId()))
                .andExpect(jsonPath("$.result.challenges[0].routineCycle").value("DAILY"))
                .andExpect(jsonPath("$.result.challenges[0].reward").value(15))
                .andExpect(jsonPath("$.result.challenges[0].participantCount").value(0))
                .andExpect(jsonPath("$.result.challenges[0].verificationPostCount").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.nextCursor").value(newer.getId()));

        // 다음 페이지: cursor=newer.id → older 한 건, hasNext=false, nextCursor=null
        mockMvc.perform(get("/api/challenges").with(user(viewer))
                        .param("keyword", "mvctag").param("size", "1")
                        .param("cursor", String.valueOf(newer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.challenges.length()").value(1))
                .andExpect(jsonPath("$.result.challenges[0].challengeId").value(older.getId()))
                // reward를 안 준 챌린지는 0으로 내려간다(엔티티 빌더 기본값). null이 아니다.
                .andExpect(jsonPath("$.result.challenges[0].reward").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("잘못된 category 값은 400을 반환한다")
    void getChallenges_InvalidCategory_Returns400() throws Exception {
        mockMvc.perform(get("/api/challenges").with(user(viewer)).param("category", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("참여하지 않은 회원의 상세는 200과 참여자 수·인증 게시글 수·오늘 완료자 수를 싣고 participating=false")
    void getChallenge_Existing_Returns200() throws Exception {
        Challenge c = persistChallenge();
        persistParticipantWithTodayVerification(c);

        mockMvc.perform(get("/api/challenges/{id}", c.getId()).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.challengeId").value(c.getId()))
                .andExpect(jsonPath("$.result.name").value("물 1L 마시기"))
                .andExpect(jsonPath("$.result.imageUrl").value("https://img/water.png"))
                .andExpect(jsonPath("$.result.routineCycle").value("DAILY"))
                // 달성 보상. 엔티티에 저장된 값이 그대로 실린다.
                .andExpect(jsonPath("$.result.reward").value(30))
                .andExpect(jsonPath("$.result.participantCount").value(1))
                .andExpect(jsonPath("$.result.verificationPostCount").value(1))
                // 조회자가 참여하지 않았으므로 false다.
                .andExpect(jsonPath("$.result.participating").value(false));
    }

    @Test
    @DisplayName("로그인한 참여자가 상세를 보면 participating=true")
    void getChallenge_LoggedInParticipant_ReturnsParticipatingTrue() throws Exception {
        Challenge c = persistChallenge();
        Member me = Member.builder()
                .email("mvc-part@ex.com").nickname("mvcPart")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mvc-part-sid").build();
        em.persist(me);
        em.persist(MemberChallenge.builder()
                .member(me).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build());
        em.flush();

        mockMvc.perform(get("/api/challenges/{id}", c.getId())
                        .with(user(new CustomUserDetails(me.getId(), Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.participating").value(true))
                // 인증하기 버튼을 그리는 두 값이 함께 나가는지 고정한다. 필드 이름이 클라이언트와의
                // 계약이라, 판정 로직을 보는 서비스 테스트만으로는 직렬화 이름이 갈리는 변경을 못 잡는다.
                // 참여만 하고 아직 인증한 적이 없으므로 false 다.
                .andExpect(jsonPath("$.result.verifiedInCurrentPeriod").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 챌린지 상세는 404를 반환한다")
    void getChallenge_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/challenges/{id}", 999999L).with(user(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHALLENGE404_1"));
    }

    @Test
    @DisplayName("숫자가 아닌 challengeId는 400을 반환한다")
    void getChallenge_MalformedId_Returns400() throws Exception {
        mockMvc.perform(get("/api/challenges/{id}", "abc").with(user(viewer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정의되지 않은 POST도 미인증이면 401이다")
    void postChallenges_Unauthenticated_IsRejected() throws Exception {
        // SecurityConfig가 PUBLIC_URIS 외 모든 요청을 authenticated로 두므로 메서드와
        // 무관하게 막힌다. 미인증 응답은 AuthenticationEntryPointImpl이 401로 낸다 —
        // 매핑되지 않은 경로라도 인증이 먼저 걸리므로 404가 아니라 401이다.
        mockMvc.perform(post("/api/challenges"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 없이 목록을 요청하면 거부된다(401) — 이 서비스에는 게스트가 없다")
    void getChallenges_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(get("/api/challenges"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 없이 상세를 요청하면 거부된다(401)")
    void getChallenge_Unauthenticated_IsRejected() throws Exception {
        Challenge c = persistChallenge();

        mockMvc.perform(get("/api/challenges/{id}", c.getId()))
                .andExpect(status().isUnauthorized());
    }
}
