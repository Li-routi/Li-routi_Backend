package com.lirouti.domain.verification.controller;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("ChallengeVerificationController MockMvc 테스트")
class ChallengeVerificationControllerTest {

    private static final String VALID_KEY =
            "challenge-verifications-staging/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";

    /**
     * 이미 저장된 인증을 만드는 fixture 용 공개 key.
     *
     * <p>대기 key 를 넣어 두면 "대기 경로가 DB 에 남는 것"을 정상으로 굳혀, 그런 회귀가 생겨도
     * 테스트가 잡지 못한다. 그래서 공개 prefix 로 둔다.
     *
     * <p><b>{@link #VALID_KEY} 에서 계산한 값이 아니다.</b> 승격은 UUID 를 새로 뽑으므로
     * 대기 key 로부터 공개 key 를 미리 알 수 없다. 승격 결과는 아래 {@code PROMOTED_KEY} 로 본다.
     */
    private static final String PUBLIC_KEY =
            "challenge-verifications/dddddddd-dddd-4ddd-8ddd-dddddddddddd.jpg";

    /** 승격된 key 의 모양. UUID 는 매번 달라 값으로는 못 박지 못한다. */
    private static final Pattern PROMOTED_KEY = Pattern.compile(
            ".*/challenge-verifications/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    private Member persistMember() {
        return persistMember("vermvc");
    }

    // email·social_id에 유니크 제약이 있어 한 테스트에서 회원을 둘 이상 만들 때는 접두사를 달리한다.
    private Member persistMember(String tag) {
        Member m = Member.builder()
                .email(tag + "@ex.com").nickname(tag)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag + "-sid").build();
        em.persist(m);
        return m;
    }

    private ChallengeVerification persistVerification(Member author, Challenge c) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(author).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(LocalDate.now(ZoneId.of("Asia/Seoul")))
                .verifiedAt(LocalDateTime.now())
                .imageUrl(PUBLIC_KEY).content("신고 대상")
                .build();
        em.persist(v);
        return v;
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
    @DisplayName("인증 없이 인증하기를 요청하면 거부된다(401)")
    void verify_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(post("/api/challenges/{id}/verifications", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_KEY, "hi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("피드 조회는 로그인이 필요하다(401)")
    void getFeed_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(get("/api/challenges/{id}/verifications", 1L))
                .andExpect(status().isUnauthorized());
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
                .andExpect(jsonPath("$.result.imageUrl").value(startsWith("http")))
                // 승격된 공개 key 여야 한다. 대기 경로가 그대로 나가면 심사 전 사진이 공개된 것이다.
                .andExpect(jsonPath("$.result.imageUrl").value(matchesPattern(PROMOTED_KEY)))
                // 승격은 UUID 를 새로 뽑는다. 올린 key 의 UUID 가 그대로 나오면 덮어쓰기가 열린다.
                .andExpect(jsonPath("$.result.imageUrl")
                        .value(not(containsString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"))));
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
    @DisplayName("피드 조회는 200과 닉네임·코멘트·mine이 담긴 카드를 돌려준다")
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
                // JSON 으로 나가는 필드 이름이 클라이언트와의 계약이라 여기서 고정한다.
                // 판정 로직은 FeedMineFlagTest 가 보지만 그쪽은 DTO 접근자를 부르므로
                // 직렬화 이름이 접근자와 갈라지는 변경(@JsonProperty, 네이밍 전략 등)은
                // 컴파일도 테스트도 통과한 채 계약만 깬다. 그 경우를 잡는 것이 이 줄이다.
                // 내가 올린 인증이라 true 다.
                .andExpect(jsonPath("$.result.verifications[0].mine").value(true))
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

    // ── 인증 신고  ──
    @Test
    @DisplayName("인증 없이 신고를 요청하면 거부된다(401)")
    void report_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(post("/api/challenges/{cid}/verifications/{vid}/reports", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"부적절\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("신고하면 200과 reportId·verificationId를 돌려주고, 사유는 생략할 수 있다")
    void report_Success() throws Exception {
        Member author = persistMember("verauthor");
        Member reporter = persistMember("verreporter");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);
        em.flush();

        mockMvc.perform(post("/api/challenges/{cid}/verifications/{vid}/reports", c.getId(), v.getId())
                        .with(user(principal(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))   // reason 생략 — 사유 없이 바로 신고할 수 있어야 한다
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.verificationId").value(v.getId()))
                .andExpect(jsonPath("$.result.reportId").isNumber());
    }

    @Test
    @DisplayName("같은 인증을 두 번 신고하면 409")
    void report_Duplicate_Returns409() throws Exception {
        Member author = persistMember("verauthor2");
        Member reporter = persistMember("verreporter2");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);
        em.flush();

        String url = "/api/challenges/" + c.getId() + "/verifications/" + v.getId() + "/reports";
        mockMvc.perform(post(url).with(user(principal(reporter)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(url).with(user(principal(reporter)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE409_4"));
    }

    @Test
    @DisplayName("경로의 챌린지에 속하지 않은 인증을 신고하면 404")
    void report_VerificationOfAnotherChallenge_Returns404() throws Exception {
        Member author = persistMember("verauthor3");
        Member reporter = persistMember("verreporter3");
        Challenge c = persistChallenge();
        Challenge other = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);
        em.flush();

        // 인증은 c에 속하는데 other 경로로 신고 → 404
        mockMvc.perform(post("/api/challenges/{cid}/verifications/{vid}/reports", other.getId(), v.getId())
                        .with(user(principal(reporter)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHALLENGE404_2"));
    }

    @Test
    @DisplayName("신고한 인증은 내 피드에서 사라지지만 다른 회원의 피드에는 남는다")
    void getFeed_ExcludesMyReportedVerificationOnly() throws Exception {
        Member author = persistMember("verauthor4");
        Member reporter = persistMember("verreporter4");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);
        em.flush();

        mockMvc.perform(post("/api/challenges/{cid}/verifications/{vid}/reports", c.getId(), v.getId())
                        .with(user(principal(reporter)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // 신고자에게는 빈 피드
        mockMvc.perform(get("/api/challenges/{id}/verifications", c.getId())
                        .with(user(principal(reporter))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.verifications").isEmpty());

        // 작성자(다른 회원)에게는 그대로 보인다 — 신고는 삭제가 아니다
        mockMvc.perform(get("/api/challenges/{id}/verifications", c.getId())
                        .with(user(principal(author))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.verifications.length()").value(1))
                .andExpect(jsonPath("$.result.verifications[0].verificationId").value(v.getId()));
    }

    // ── 내 인증 목록  ──

    @Test
    @DisplayName("인증 없이 내 인증 목록을 요청하면 거부된다(401)")
    void getMyVerifications_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(get("/api/challenges/{id}/verifications/me", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 인증 목록은 nickname 없이 verifiedDate와 currentStreak을 싣는다")
    void getMyVerifications_ReturnsMineWithDateAndStreak() throws Exception {
        Member author = persistMember("vermine1");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);
        em.flush();

        mockMvc.perform(get("/api/challenges/{id}/verifications/me", c.getId())
                        .with(user(principal(author))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHALLENGE200_9"))
                .andExpect(jsonPath("$.result.verifications.length()").value(1))
                .andExpect(jsonPath("$.result.verifications[0].verificationId").value(v.getId()))
                .andExpect(jsonPath("$.result.verifications[0].verifiedDate").exists())
                .andExpect(jsonPath("$.result.verifications[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.result.currentStreak").exists())
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    @DisplayName("남의 인증은 내 목록에 나오지 않는다")
    void getMyVerifications_ExcludesOthers() throws Exception {
        Member author = persistMember("vermine2");
        Member viewer = persistMember("vermine2v");
        Challenge c = persistChallenge();
        persistVerification(author, c);
        persistParticipation(viewer, c);
        em.flush();

        mockMvc.perform(get("/api/challenges/{id}/verifications/me", c.getId())
                        .with(user(principal(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.verifications").isEmpty());
    }

    @Test
    @DisplayName("참여한 적 없는 챌린지의 내 인증을 조회하면 409다")
    void getMyVerifications_NeverParticipated_Returns409() throws Exception {
        Member stranger = persistMember("vermine3");
        Challenge c = persistChallenge();
        em.flush();

        mockMvc.perform(get("/api/challenges/{id}/verifications/me", c.getId())
                        .with(user(principal(stranger))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE409_2"));
    }

    @Test
    @DisplayName("메모 수정은 200과 바뀐 content를 돌려준다")
    void updateMemo_Success() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(me, c);
        em.flush();

        mockMvc.perform(patch("/api/challenges/{cid}/verifications/{vid}", c.getId(), v.getId())
                        .with(user(principal(me)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "고친 메모"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.verificationId").value(v.getId()))
                .andExpect(jsonPath("$.result.content").value("고친 메모"));
    }

    @Test
    @DisplayName("메모가 255자를 넘으면 400")
    void updateMemo_TooLong_IsBadRequest() throws Exception {
        Member me = persistMember();
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(me, c);
        em.flush();

        mockMvc.perform(patch("/api/challenges/{cid}/verifications/{vid}", c.getId(), v.getId())
                        .with(user(principal(me)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"%s\"}".formatted("가".repeat(256))))
                .andExpect(status().isBadRequest());
    }
}
