package com.lirouti.domain.challenge.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 상세 화면의 {@code verifiedInCurrentPeriod}.
 *
 * <p>인증하기 버튼의 활성 여부가 이 값으로 갈린다. <b>틀리면 이미 인증한 사람에게 버튼이
 * 열리거나, 아직 안 한 사람의 버튼이 잠긴다.</b> 후자가 특히 나쁘다 — 사용자가 할 수 있는 일을
 * 못 하게 막는 것이라 문의로 이어진다.
 *
 * <p>참여 상태와의 조합을 함께 본다. 이탈했거나 한 번도 참여하지 않았으면 인증할 수 없으므로
 * 이 값도 false 여야 한다.
 */
@SpringBootTest
@Transactional
@DisplayName("상세 인증 여부 플래그 테스트")
class ChallengeDetailVerifiedFlagTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private ChallengeQueryService challengeQueryService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("flag" + n + "@ex.com").nickname("flag" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("flag-sid-" + n).build();
        em.persist(m);
        return m;
    }

    /** 현재 데이터가 전부 DAILY 라 기본값을 그대로 쓴다(엔티티가 null 이면 DAILY 로 채운다). */
    private Challenge challenge() {
        Challenge c = Challenge.builder()
                .name("플래그챌린지" + seq.incrementAndGet())
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    /**
     * 참여시키고, 인증일이 주어지면 <b>실제 인증 행까지</b> 만든다.
     *
     * 판정이 lastVerifiedDate 가 아니라 인증 테이블을 보므로 행이 없으면 false 가 된다.
     * 재참여가 lastVerifiedDate 를 초기화해 그 값으로는 판정할 수 없기 때문이다.
     */
    private MemberChallenge participate(Member m, Challenge c, LocalDate lastVerifiedDate) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(lastVerifiedDate == null ? 0 : 1)
                .lastVerifiedDate(lastVerifiedDate)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        if (lastVerifiedDate != null) {
            em.persist(ChallengeVerification.builder()
                    .memberChallenge(mc).participationRound(1)
                    .verifiedDate(lastVerifiedDate)
                    .verifiedAt(lastVerifiedDate.atTime(9, 0))
                    .imageUrl("challenge-verifications/ffffffff-ffff-4fff-8fff-ffffffffffff.jpg")
                    .content("인증").build());
        }
        em.flush();
        return mc;
    }

    private ChallengeResDTO.Detail detail(Member m, Challenge c) {
        em.flush();
        em.clear();
        return challengeQueryService.getChallenge(c.getId(), m.getId());
    }

    // ── 테스트 ──

    @Test
    @DisplayName("오늘 인증했으면 true — 버튼이 잠긴다")
    void verifiedToday_IsTrue() {
        Member me = member();
        Challenge c = challenge();
        participate(me, c, LocalDate.now(KST));

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isTrue()
        );
    }

    @Test
    @DisplayName("어제가 마지막이면 false — 오늘 다시 인증할 수 있다")
    void verifiedYesterday_IsFalse() {
        Member me = member();
        Challenge c = challenge();
        participate(me, c, LocalDate.now(KST).minusDays(1));

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

    @Test
    @DisplayName("참여만 하고 아직 인증한 적 없으면 false")
    void neverVerified_IsFalse() {
        Member me = member();
        Challenge c = challenge();
        participate(me, c, null);

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

    @Test
    @DisplayName("이탈했으면 오늘 인증 기록이 있어도 false — 인증할 수 없는 상태다")
    void leftChallenge_IsFalse() {
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c, LocalDate.now(KST));
        mc.leave();

        ChallengeResDTO.Detail result = detail(me, c);

        assertAll(
                () -> assertThat(result.participating()).isFalse(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

    @Test
    @DisplayName("참여한 적 없는 회원은 false")
    void notParticipating_IsFalse() {
        Member stranger = member();
        Challenge c = challenge();

        ChallengeResDTO.Detail result = detail(stranger, c);

        assertAll(
                () -> assertThat(result.participating()).isFalse(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isFalse()
        );
    }

@Test
    @DisplayName("같은 날 이탈 후 재참여해도 true — 하루 1회는 회차를 넘어 적용된다")
    void rejoinedSameDay_StaysTrue() {
        // given: 오늘 인증하고 이탈했다가 같은 날 다시 참여한다.
        Member me = member();
        Challenge c = challenge();
        MemberChallenge mc = participate(me, c, LocalDate.now(KST));
        mc.leave();
        mc.rejoin(LocalDateTime.now());

        // when
        ChallengeResDTO.Detail result = detail(me, c);

        // then: 예전에는 여기서 false 가 나와 버튼이 다시 열렸다. rejoin 이 lastVerifiedDate 를
        // null 로 만들기 때문인데, 판정이 인증 테이블을 직접 보도록 바뀌어 그 우회가 막혔다.
        // 쓰기도 같은 기준으로 막는다(RejoinDailyOnceTest).
        assertAll(
                () -> assertThat(result.participating()).isTrue(),
                () -> assertThat(result.verifiedInCurrentPeriod()).isTrue()
        );
    }
}
