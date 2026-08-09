package com.lirouti.domain.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;

/**
 * 인증 메모 수정.
 *
 * <p><b>남의 글이 막히는가가 이 테스트의 주제다.</b> 수정은 남의 기록을 바꾸는 일이라
 * 소유자 판정을 틀리면 그대로 사고다. 그래서 "내 것이 바뀌는가"만큼이나
 * "남의 것이 안 바뀌는가"를 본다.
 *
 * <p>사진과 인증 시각이 그대로인지도 함께 본다 — 재인증 메서드를 재사용하면 둘이 함께
 * 바뀌는데, 그러면 오타 하나 고치는데 피드 순서가 흔들린다.
 */
@SpringBootTest
@Transactional
@DisplayName("인증 메모 수정 테스트")
class VerificationMemoUpdateTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY =
            "challenge-verifications/dddddddd-dddd-4ddd-8ddd-dddddddddddd.jpg";

    @Autowired
    private ChallengeVerificationService challengeVerificationService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("memo" + n + "@ex.com").nickname("memo" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("memo-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge() {
        Challenge c = Challenge.builder()
                .name("메모챌린지" + seq.incrementAndGet())
                .category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    /** 지정한 날짜의 인증을 만든다. 어제 것을 만들어 "날짜 제한이 없는지"를 볼 수 있다. */
    private ChallengeVerification verification(Member author, Challenge c, LocalDate date) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(author).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(date).verifiedAt(date.atTime(9, 0))
                .imageUrl(KEY).content("원래 메모").build();
        em.persist(v);
        em.flush();
        return v;
    }

    private ChallengeVerificationReqDTO.UpdateMemo memo(String content) {
        return new ChallengeVerificationReqDTO.UpdateMemo(content);
    }

    // ── 테스트 ──

    @Test
    @DisplayName("내 인증의 메모를 고치면 사진과 인증 시각은 그대로다")
    void updateMemo_KeepsPhotoAndVerifiedAt() {
        // given
        Member me = member();
        Challenge c = challenge();
        ChallengeVerification v = verification(me, c, LocalDate.now(KST));
        LocalDateTime originalVerifiedAt = v.getVerifiedAt();

        // when
        ChallengeVerificationResDTO.MemoUpdate result =
                challengeVerificationService.updateMemo(me.getId(), c.getId(), v.getId(), memo("고친 메모"));
        em.flush();
        em.clear();

        // then
        ChallengeVerification reloaded = em.find(ChallengeVerification.class, v.getId());
        assertAll(
                () -> assertThat(result.content()).isEqualTo("고친 메모"),
                () -> assertThat(reloaded.getContent()).isEqualTo("고친 메모"),
                // 재인증 메서드를 재사용했다면 이 둘이 함께 바뀐다
                () -> assertThat(reloaded.getImageUrl()).isEqualTo(KEY),
                () -> assertThat(reloaded.getVerifiedAt()).isEqualTo(originalVerifiedAt)
        );
    }

    @Test
    @DisplayName("남의 인증은 고칠 수 없다 — 없는 인증과 같은 404다")
    void updateMemo_OthersVerification_IsNotFound() {
        // given
        Challenge c = challenge();
        ChallengeVerification others = verification(member(), c, LocalDate.now(KST));
        Member intruder = member();
        em.flush();

        // when & then
        assertThatThrownBy(() -> challengeVerificationService
                .updateMemo(intruder.getId(), c.getId(), others.getId(), memo("가로챈 메모")))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND);

        em.clear();
        assertThat(em.find(ChallengeVerification.class, others.getId()).getContent())
                .isEqualTo("원래 메모");
    }

    @Test
    @DisplayName("어제 쓴 메모도 고칠 수 있다 — 사진과 달리 날짜 제한이 없다")
    void updateMemo_PastVerification_IsAllowed() {
        // given
        Member me = member();
        Challenge c = challenge();
        ChallengeVerification yesterday = verification(me, c, LocalDate.now(KST).minusDays(1));

        // when
        challengeVerificationService.updateMemo(me.getId(), c.getId(), yesterday.getId(), memo("뒤늦게 고침"));
        em.flush();
        em.clear();

        // then
        assertThat(em.find(ChallengeVerification.class, yesterday.getId()).getContent())
                .isEqualTo("뒤늦게 고침");
    }

    @Test
    @DisplayName("빈 값을 보내면 메모가 지워진다 — 공백만 보내도 null 이다")
    void updateMemo_Blank_ClearsMemo() {
        // given
        Member me = member();
        Challenge c = challenge();
        ChallengeVerification v = verification(me, c, LocalDate.now(KST));

        // when: 공백만 보낸다. 빈 문자열로 남으면 "메모 없음"이 두 가지 모양이 된다
        ChallengeVerificationResDTO.MemoUpdate result =
                challengeVerificationService.updateMemo(me.getId(), c.getId(), v.getId(), memo("   "));
        em.flush();
        em.clear();

        // then
        assertAll(
                () -> assertThat(result.content()).isNull(),
                () -> assertThat(em.find(ChallengeVerification.class, v.getId()).getContent()).isNull()
        );
    }

    @Test
    @DisplayName("신고로 가려진 인증은 고칠 수 없다 — 본인에게도 안 보이는 글이다")
    void updateMemo_HiddenVerification_IsNotFound() {
        // given
        Member me = member();
        Challenge c = challenge();
        ChallengeVerification v = verification(me, c, LocalDate.now(KST));
        v.hide(LocalDateTime.now(KST));
        em.flush();

        // when & then
        assertThatThrownBy(() -> challengeVerificationService
                .updateMemo(me.getId(), c.getId(), v.getId(), memo("되살리기 시도")))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("경로의 챌린지와 인증의 챌린지가 다르면 막는다")
    void updateMemo_ChallengeMismatch_IsNotFound() {
        // given
        Member me = member();
        Challenge mine = challenge();
        Challenge other = challenge();
        ChallengeVerification v = verification(me, mine, LocalDate.now(KST));
        em.flush();

        // when & then: 내 인증이지만 경로의 챌린지가 다르다
        assertThatThrownBy(() -> challengeVerificationService
                .updateMemo(me.getId(), other.getId(), v.getId(), memo("엉뚱한 경로")))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND);
    }
}
