package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.util.TimeUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
@DisplayName("ChallengeCommandService 인증 테스트")
class ChallengeVerificationCommandServiceTest {

    private static final String KEY_1 = "challenge-verifications/11111111-1111-4111-8111-111111111111.jpg";
    private static final String KEY_2 = "challenge-verifications/22222222-2222-4222-8222-222222222222.png";

    @Autowired
    private ChallengeCommandService challengeCommandService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    // ── 헬퍼 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("ver-m" + n + "@ex.com").nickname("vm" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("ver-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge(boolean active) {
        Challenge c = Challenge.builder()
                .name("ver챌린지").category(ChallengeCategory.HEALTH).active(active).build();
        em.persist(c);
        return c;
    }

    /** lastVerifiedDate·currentStreak을 원하는 상태로 만들어 둔 참여를 저장한다. */
    private MemberChallenge join(Member m, Challenge c, boolean active, LocalDate lastVerified, int streak) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(streak)
                .lastVerifiedDate(lastVerified)
                .joinedAt(LocalDateTime.now()).active(active).build();
        em.persist(mc);
        return mc;
    }

    private ChallengeReqDTO.Verify request(String mediaKey, String content) {
        return new ChallengeReqDTO.Verify(mediaKey, content);
    }

    private List<ChallengeVerification> verificationsOf(MemberChallenge mc) {
        return em.createQuery(
                        "select v from ChallengeVerification v where v.memberChallenge.id = :id",
                        ChallengeVerification.class)
                .setParameter("id", mc.getId())
                .getResultList();
    }

    private LocalDate today() {
        return LocalDate.now(TimeUtil.KST);
    }

    // ── 인증 성공 & 스트릭 ──
    @Test
    @DisplayName("첫 인증이면 인증 행이 생기고 스트릭이 1이 된다")
    void verify_FirstTime_StartsStreakAtOne() {
        Member m = member();
        Challenge c = challenge(true);
        MemberChallenge mc = join(m, c, true, null, 0);
        em.flush();

        ChallengeResDTO.Verification result =
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, "첫 인증"));

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.reverified()).isFalse();
        assertThat(result.verifiedDate()).isEqualTo(today());
        assertThat(result.content()).isEqualTo("첫 인증");
        assertThat(verificationsOf(mc)).hasSize(1);
        assertThat(mc.getLastVerifiedDate()).isEqualTo(today());
    }

    @Test
    @DisplayName("어제 인증했으면 스트릭이 1 오른다")
    void verify_VerifiedYesterday_IncrementsStreak() {
        Member m = member();
        Challenge c = challenge(true);
        MemberChallenge mc = join(m, c, true, today().minusDays(1), 3);
        em.flush();

        ChallengeResDTO.Verification result =
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, null));

        assertThat(result.currentStreak()).isEqualTo(4);
        assertThat(mc.getCurrentStreak()).isEqualTo(4);
    }

    @Test
    @DisplayName("연속이 끊긴 뒤 인증하면 스트릭이 1로 다시 시작한다")
    void verify_StreakBroken_RestartsAtOne() {
        Member m = member();
        Challenge c = challenge(true);
        MemberChallenge mc = join(m, c, true, today().minusDays(3), 10);
        em.flush();

        ChallengeResDTO.Verification result =
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, null));

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(mc.getCurrentStreak()).isEqualTo(1);
    }

    // ── 당일 재인증(덮어쓰기) ──
    @Test
    @DisplayName("같은 날 다시 인증하면 행을 새로 만들지 않고 사진·코멘트를 덮어쓴다")
    void verify_SameDayAgain_OverwritesInsteadOfInserting() {
        Member m = member();
        Challenge c = challenge(true);
        MemberChallenge mc = join(m, c, true, null, 0);
        em.flush();

        challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, "처음"));
        ChallengeResDTO.Verification second =
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_2, "바꿈"));

        assertThat(second.reverified()).isTrue();
        assertThat(second.content()).isEqualTo("바꿈");
        assertThat(second.imageUrl()).endsWith(KEY_2);

        // 하루에 한 행이라는 사실이 유지되어야 한다.
        List<ChallengeVerification> rows = verificationsOf(mc);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getImageUrl()).isEqualTo(KEY_2);
    }

    @Test
    @DisplayName("당일 재인증은 스트릭을 올리지 않는다")
    void verify_SameDayAgain_DoesNotIncrementStreak() {
        Member m = member();
        Challenge c = challenge(true);
        MemberChallenge mc = join(m, c, true, today().minusDays(1), 5);
        em.flush();

        challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, null));
        ChallengeResDTO.Verification second =
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_2, null));

        // 어제 → 오늘로 한 번만 올라 6이어야 하고, 두 번째 인증으로 7이 되면 안 된다.
        assertThat(second.currentStreak()).isEqualTo(6);
        assertThat(mc.getCurrentStreak()).isEqualTo(6);
    }

    // ── 참여 상태 ──
    @Test
    @DisplayName("참여한 적 없는 챌린지에 인증하면 409")
    void verify_NeverParticipated_ThrowsNotParticipating() {
        Member m = member();
        Challenge c = challenge(true);
        em.flush();

        assertThatThrownBy(() ->
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, null)))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.NOT_PARTICIPATING);
    }

    @Test
    @DisplayName("그만둔 챌린지에 인증하면 409")
    void verify_LeftChallenge_ThrowsNotParticipating() {
        Member m = member();
        Challenge c = challenge(true);
        join(m, c, false, null, 0);
        em.flush();

        assertThatThrownBy(() ->
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, null)))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.NOT_PARTICIPATING);
    }

    @Test
    @DisplayName("비활성 챌린지라도 참여 중이면 인증할 수 있다 (이탈과 같은 기준)")
    void verify_InactiveChallengeButParticipating_Succeeds() {
        Member m = member();
        Challenge c = challenge(false);
        join(m, c, true, null, 0);
        em.flush();

        ChallengeResDTO.Verification result =
                challengeCommandService.verify(m.getId(), c.getId(), request(KEY_1, null));

        assertThat(result.currentStreak()).isEqualTo(1);
    }

    // ── 미디어 key 검증 ──
    @Test
    @DisplayName("발급 규칙에 맞지 않는 mediaKey면 400이고 인증이 저장되지 않는다")
    void verify_InvalidMediaKey_ThrowsAndSavesNothing() {
        Member m = member();
        Challenge c = challenge(true);
        MemberChallenge mc = join(m, c, true, null, 0);
        em.flush();

        assertThatThrownBy(() ->
                challengeCommandService.verify(m.getId(), c.getId(), request("profiles/whatever.jpg", null)))
                .isInstanceOf(MediaException.class)
                .hasFieldOrPropertyWithValue("code", MediaErrorCode.INVALID_MEDIA_KEY);

        assertThat(verificationsOf(mc)).isEmpty();
        assertThat(mc.getCurrentStreak()).isZero();
    }
}
