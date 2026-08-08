package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.client.AnthropicVerificationReviewClient;
import com.lirouti.domain.challenge.client.VerificationReview;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;

/**
 * 하루 1회는 참여 회차를 넘어 적용된다.
 *
 * <p>예전에는 나갔다 다시 들어오면 같은 날 또 인증할 수 있었다. 두 곳이 동시에 뚫려서다 —
 * 버튼은 {@code lastVerifiedDate} 를 봤는데 재참여가 그것을 {@code null} 로 만들었고,
 * 저장은 회차를 조건에 넣어 찾아서 새 회차에서는 기존 인증을 못 봤다.
 * <b>실제로 운영에서 두 건이 그렇게 생겼다.</b>
 *
 * <p>그래서 <b>쓰기와 조회를 함께</b> 본다. 한쪽만 고치면 버튼은 잠겼는데 API 로는 되거나,
 * 그 반대가 된다.
 *
 * <p>사진 검증과 AI 심사는 외부 호출이라 mock 한다. 여기서 보려는 것은 회차 판정이다.
 */
@SpringBootTest
@Transactional
@DisplayName("재참여 후 하루 1회 유지 테스트")
class RejoinDailyOnceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String STAGING_KEY =
            "challenge-verifications-staging/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee.jpg";
    private static final String PUBLIC_KEY =
            "challenge-verifications/11111111-1111-4111-8111-111111111111.jpg";

    @Autowired
    private ChallengeCommandService challengeCommandService;
    @Autowired
    private ChallengeQueryService challengeQueryService;
    @Autowired
    private ChallengeVerificationRepository verificationRepository;

    @MockitoBean
    private MediaService mediaService;
    @MockitoBean
    private AnthropicVerificationReviewClient reviewClient;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void setUp() {
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());
        // 승격은 S3 복사라 목으로 둔다. 이 테스트가 보는 것은 심사 결과이지 승격이 아니다.
        // promote 는 대기 key 를 받아 UUID 가 새로 뽑힌 공개 key 를 돌려준다.
        // 받은 값을 그대로 돌려주면 승격이 아무 일도 안 해도 테스트가 통과한다.
        when(mediaService.promote(any(), any(), any())).thenReturn(PUBLIC_KEY);
        when(mediaService.resolvePublicUrl(any())).thenReturn("https://cdn.example.com/" + PUBLIC_KEY);
        when(reviewClient.review(any(), any(), any())).thenReturn(VerificationReview.pass());
    }

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("rj" + n + "@ex.com").nickname("rj" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("rj-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge() {
        Challenge c = Challenge.builder()
                .name("재참여챌린지" + seq.incrementAndGet())
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge participate(Member m, Challenge c) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        em.flush();
        return mc;
    }

    private ChallengeVerificationReqDTO.Verify request() {
        return new ChallengeVerificationReqDTO.Verify(STAGING_KEY, "인증");
    }

    private void verify(Member m, Challenge c) {
        challengeCommandService.verify(m.getId(), c.getId(), request());
    }

    /** 나갔다 다시 들어온다. 회차가 오르고 lastVerifiedDate 가 초기화된다. */
    private void leaveAndRejoin(MemberChallenge mc) {
        mc.leave();
        mc.rejoin(LocalDateTime.now());
        em.flush();
    }

    // ── 테스트 ──

    @Test
    @DisplayName("오늘 인증하고 나갔다 들어오면 다시 인증할 수 없다 — 회차로 우회되지 않는다")
    void rejoin_SameDay_CannotVerifyAgain() {
        // given
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c);
        verify(me, c);
        em.flush();

        leaveAndRejoin(mc);

        // when & then
        assertThatThrownBy(() -> verify(me, c))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.ALREADY_VERIFIED_TODAY);

        // 인증 행이 늘지 않았다 — 예전에는 여기서 하루 두 건이 됐다
        em.flush();
        em.clear();
        assertThat(verificationRepository.findByMemberChallengeIdAndVerifiedDate(
                mc.getId(), LocalDate.now(KST))).isPresent();
        assertThat(em.createQuery(
                        "select count(v) from ChallengeVerification v where v.memberChallenge.id = :id",
                        Long.class)
                .setParameter("id", mc.getId())
                .getSingleResult()).isEqualTo(1L);
    }

    @Test
    @DisplayName("나갔다 들어와도 버튼이 잠긴 채다 — 쓰기만 막고 조회를 안 고치면 화면이 어긋난다")
    void rejoin_SameDay_ButtonStaysDisabled() {
        // given
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c);
        verify(me, c);
        em.flush();

        leaveAndRejoin(mc);
        em.clear();

        // when
        ChallengeResDTO.Detail detail = challengeQueryService.getChallenge(c.getId(), me.getId());

        // then: lastVerifiedDate 는 rejoin 이 null 로 만들었으므로 그것을 보면 false 가 나온다.
        // 인증 테이블을 직접 봐야 true 다.
        assertAll(
                () -> assertThat(detail.participating()).isTrue(),
                () -> assertThat(detail.verifiedInCurrentPeriod()).isTrue()
        );
    }

    @Test
    @DisplayName("어제 인증했다면 재참여 후 오늘 인증할 수 있다 — 막는 것은 '오늘'뿐이다")
    void rejoin_VerifiedYesterday_CanVerifyToday() {
        // given: 어제 인증한 이력을 직접 만든다(인증 API 는 오늘 것만 만든다)
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c);
        ChallengeVerification yesterday = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(LocalDate.now(KST).minusDays(1))
                .verifiedAt(LocalDateTime.now().minusDays(1))
                .imageUrl(PUBLIC_KEY).content("어제").build();
        em.persist(yesterday);

        leaveAndRejoin(mc);
        em.clear();

        // when & then
        assertThatCode(() -> verify(me, c)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("나가지 않고 같은 날 다시 인증하면 덮어쓴다 — 당일 재인증은 그대로다")
    void sameRound_SameDay_Overwrites() {
        // given
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c);
        verify(me, c);
        em.flush();

        // when: 회차가 그대로이므로 덮어쓰기다
        ChallengeVerificationResDTO.Verification result =
                challengeCommandService.verify(me.getId(), c.getId(),
                        new ChallengeVerificationReqDTO.Verify(STAGING_KEY, "고친 코멘트"));
        em.flush();
        em.clear();

        // then
        assertAll(
                () -> assertThat(result.reverified()).isTrue(),
                () -> assertThat(em.createQuery(
                                "select count(v) from ChallengeVerification v"
                                        + " where v.memberChallenge.id = :id", Long.class)
                        .setParameter("id", mc.getId())
                        .getSingleResult()).isEqualTo(1L)
        );
    }

    @Test
    @DisplayName("재참여해도 오늘 완료자 수에 남는다 — 버튼과 집계의 기준이 같아야 한다")
    void rejoin_StillCountedAsTodayCompletion() {
        // given: 오늘 인증하고 나갔다 다시 들어온다(회차 2)
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c);
        verify(me, c);
        em.flush();

        leaveAndRejoin(mc);
        em.clear();

        // when
        ChallengeResDTO.Detail detail = challengeQueryService.getChallenge(c.getId(), me.getId());

        // then: 버튼은 잠기는데(이미 했으므로) 집계에서는 빠지면 두 숫자가 서로 다른 말을 한다.
        // 집계가 회차를 걸면 여기서 0이 나온다.
        assertAll(
                () -> assertThat(detail.verifiedInCurrentPeriod()).isTrue(),
                () -> assertThat(detail.todayCompletionCount()).isEqualTo(1L)
        );
    }
}
