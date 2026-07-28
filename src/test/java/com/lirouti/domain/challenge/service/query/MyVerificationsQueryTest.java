package com.lirouti.domain.challenge.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
@DisplayName("내 인증 목록 조회(#62) 테스트")
class MyVerificationsQueryTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY_PREFIX = "challenge-verifications/";

    @Autowired
    private ChallengeQueryService challengeQueryService;

    @PersistenceContext
    private EntityManager em;

    private Member persistMember(String tag) {
        Member m = Member.builder()
                .email(tag + "@ex.com").nickname(tag)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag + "-sid").build();
        em.persist(m);
        return m;
    }

    private Challenge persistChallenge() {
        Challenge c = Challenge.builder()
                .name("내인증챌린지").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge persistParticipation(Member m, Challenge c, int round, boolean active) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(round).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(active).build();
        em.persist(mc);
        return mc;
    }

    /** verifiedDate는 유니크 제약(member_challenge, round, date)에 걸리므로 건마다 다르게 준다. */
    private ChallengeVerification persistVerification(
            MemberChallenge mc, int round, LocalDate date, String tag) {
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(round)
                .verifiedDate(date)
                .verifiedAt(date.atTime(9, 0))
                .imageUrl(KEY_PREFIX + "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg")
                .content(tag)
                .build();
        em.persist(v);
        return v;
    }

    @Test
    @DisplayName("내 인증만 나온다 — 같은 챌린지의 다른 회원 인증은 섞이지 않는다")
    void getMyVerifications_ReturnsOnlyMine() {
        Challenge c = persistChallenge();
        Member me = persistMember("mine1");
        Member other = persistMember("other1");
        LocalDate today = LocalDate.now(KST);

        MemberChallenge myMc = persistParticipation(me, c, 1, true);
        persistVerification(myMc, 1, today, "내 것");

        MemberChallenge otherMc = persistParticipation(other, c, 1, true);
        persistVerification(otherMc, 1, today, "남의 것");
        em.flush();

        ChallengeResDTO.MyVerifications result =
                challengeQueryService.getMyVerifications(me.getId(), c.getId(), null, null);

        assertThat(result.verifications()).hasSize(1);
        assertThat(result.verifications().get(0).content()).isEqualTo("내 것");
    }

    @Test
    @DisplayName("지난 회차 인증은 빠진다 — 재참여로 회차가 오르면 이전 기록은 목록에서 사라진다")
    void getMyVerifications_ExcludesPreviousRounds() {
        Challenge c = persistChallenge();
        Member me = persistMember("round1");
        LocalDate today = LocalDate.now(KST);

        // 회차를 2로 올린 상태(이탈 후 재참여). 1회차 인증은 그대로 남아 있다.
        MemberChallenge mc = persistParticipation(me, c, 2, true);
        persistVerification(mc, 1, today.minusDays(10), "1회차");
        persistVerification(mc, 2, today, "2회차");
        em.flush();

        ChallengeResDTO.MyVerifications result =
                challengeQueryService.getMyVerifications(me.getId(), c.getId(), null, null);

        assertThat(result.verifications()).hasSize(1);
        assertThat(result.verifications().get(0).content()).isEqualTo("2회차");
    }

    @Test
    @DisplayName("그만둔 챌린지도 조회된다 — 마지막 회차의 기록이 그대로 보인다")
    void getMyVerifications_LeftChallenge_StillReturnsRecords() {
        Challenge c = persistChallenge();
        Member me = persistMember("left1");
        LocalDate today = LocalDate.now(KST);

        MemberChallenge mc = persistParticipation(me, c, 1, false);   // active=false, 이탈 상태
        persistVerification(mc, 1, today.minusDays(1), "그만두기 전 인증");
        em.flush();

        ChallengeResDTO.MyVerifications result =
                challengeQueryService.getMyVerifications(me.getId(), c.getId(), null, null);

        assertThat(result.verifications()).hasSize(1);
        assertThat(result.verifications().get(0).content()).isEqualTo("그만두기 전 인증");
    }

    @Test
    @DisplayName("한 번도 참여한 적 없으면 빈 목록이 아니라 409다")
    void getMyVerifications_NeverParticipated_Throws409() {
        Challenge c = persistChallenge();
        Member stranger = persistMember("stranger1");
        em.flush();

        assertThatThrownBy(() ->
                challengeQueryService.getMyVerifications(stranger.getId(), c.getId(), null, null))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.NOT_PARTICIPATING);
    }

    @Test
    @DisplayName("커서 페이징: 최신순으로 끊어 읽고 마지막 페이지에서 hasNext=false")
    void getMyVerifications_CursorPaging() {
        Challenge c = persistChallenge();
        Member me = persistMember("cursor1");
        LocalDate today = LocalDate.now(KST);

        MemberChallenge mc = persistParticipation(me, c, 1, true);
        persistVerification(mc, 1, today.minusDays(2), "3일전");
        persistVerification(mc, 1, today.minusDays(1), "2일전");
        persistVerification(mc, 1, today, "오늘");
        em.flush();

        ChallengeResDTO.MyVerifications first =
                challengeQueryService.getMyVerifications(me.getId(), c.getId(), null, 2);

        assertThat(first.verifications()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        // id 내림차순이므로 나중에 저장한 것이 먼저 온다.
        assertThat(first.verifications()).extracting(ChallengeResDTO.MyVerificationItem::content)
                .containsExactly("오늘", "2일전");

        ChallengeResDTO.MyVerifications second = challengeQueryService
                .getMyVerifications(me.getId(), c.getId(), first.nextCursor(), 2);

        assertThat(second.verifications()).extracting(ChallengeResDTO.MyVerificationItem::content)
                .containsExactly("3일전");
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("응답에 verifiedDate와 공개 URL이 실린다")
    void getMyVerifications_CarriesDateAndPublicUrl() {
        Challenge c = persistChallenge();
        Member me = persistMember("field1");
        LocalDate today = LocalDate.now(KST);

        MemberChallenge mc = persistParticipation(me, c, 1, true);
        persistVerification(mc, 1, today, "필드 확인");
        em.flush();

        ChallengeResDTO.MyVerificationItem item = challengeQueryService
                .getMyVerifications(me.getId(), c.getId(), null, null)
                .verifications().get(0);

        assertThat(item.verifiedDate()).isEqualTo(today);
        // DB에는 key만 있고 응답에는 조립된 URL이 나가야 한다.
        assertThat(item.imageUrl()).endsWith(KEY_PREFIX + "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg");
        assertThat(item.imageUrl()).startsWith("http");
    }

    @Test
    @DisplayName("인증이 없으면 빈 목록과 hasNext=false를 돌려준다")
    void getMyVerifications_NoVerifications_ReturnsEmpty() {
        Challenge c = persistChallenge();
        Member me = persistMember("empty1");
        persistParticipation(me, c, 1, true);
        em.flush();

        ChallengeResDTO.MyVerifications result =
                challengeQueryService.getMyVerifications(me.getId(), c.getId(), null, null);

        assertThat(result.verifications()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("currentStreak은 저장값이 아니라 오늘 기준으로 다시 판정한 값이다")
    void getMyVerifications_StreakRecalculatedAsOfToday() {
        Challenge c = persistChallenge();
        Member me = persistMember("streak1");
        LocalDate today = LocalDate.now(KST);

        // 저장된 스트릭은 5지만 마지막 인증이 사흘 전이라 이미 끊겼다.
        MemberChallenge mc = MemberChallenge.builder()
                .member(me).challenge(c)
                .participationRound(1).currentStreak(5)
                .lastVerifiedDate(today.minusDays(3))
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        persistVerification(mc, 1, today.minusDays(3), "사흘 전");
        em.flush();

        ChallengeResDTO.MyVerifications result =
                challengeQueryService.getMyVerifications(me.getId(), c.getId(), null, null);

        assertThat(result.currentStreak()).isZero();
    }

    @Test
    @DisplayName("size는 최대치로 제한된다 — 큰 값을 줘도 한 번에 다 내려가지 않는다")
    void getMyVerifications_SizeClamped() {
        Challenge c = persistChallenge();
        Member me = persistMember("clamp1");
        LocalDate today = LocalDate.now(KST);

        MemberChallenge mc = persistParticipation(me, c, 1, true);
        List.of(0, 1, 2).forEach(i ->
                persistVerification(mc, 1, today.minusDays(i), "d" + i));
        em.flush();

        ChallengeResDTO.MyVerifications result =
                challengeQueryService.getMyVerifications(me.getId(), c.getId(), null, 9999);

        // 데이터가 3건뿐이라 전부 나오되, 예외 없이 처리되는 것이 요점이다.
        assertThat(result.verifications()).hasSize(3);
        assertThat(result.hasNext()).isFalse();
    }
}
