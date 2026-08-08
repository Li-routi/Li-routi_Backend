package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.service.command.ChallengeCommandService;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.global.util.TimeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 인증 게시글 삭제.
 *
 * <p>핵심은 <b>"인증을 취소하는 것이 아니라 글을 내리는 것"</b> 이다. 그날 인증한 사실도
 * 스트릭도 그대로 남고, 글과 사진만 내려간다.
 */
@SpringBootTest
@Transactional
@DisplayName("인증 게시글 삭제")
class VerificationDeleteTest {

    private static final String PUBLIC_KEY =
            "challenge-verifications/2026/08/09/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";

    @Autowired
    private ChallengeCommandService challengeCommandService;

    @Autowired
    private ChallengeQueryService challengeQueryService;

    @MockitoBean
    private MediaService mediaService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private Challenge challenge;
    private MemberChallenge participation;

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("del" + n + "@ex.com").nickname("del" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("del-sid-" + n).build();
        em.persist(me);

        challenge = Challenge.builder()
                .name("물 마시기").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(challenge);

        participation = MemberChallenge.builder()
                .member(me).challenge(challenge)
                .participationRound(1).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(participation);
        em.flush();

        when(mediaService.resolvePublicUrl(any())).thenReturn("https://cdn.example.com/" + PUBLIC_KEY);
    }

    private LocalDate today() {
        return LocalDate.now(TimeUtil.KST);
    }

    /** 지정한 날짜의 인증을 만든다. 스트릭은 호출부가 직접 맞춘다. */
    private ChallengeVerification verificationOn(LocalDate date) {
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(participation).participationRound(1)
                .verifiedDate(date).verifiedAt(date.atTime(10, 0))
                .imageUrl(PUBLIC_KEY).content("인증")
                .build();
        em.persist(v);
        em.flush();
        return v;
    }

    @Test
    @DisplayName("글은 내려가되 인증한 사실은 남는다 — 그날 버튼은 완료 그대로다")
    void delete_KeepsTheFactOfVerification() {
        // given
        ChallengeVerification v = verificationOn(today());
        participation.applyVerification(today());
        em.flush();

        // when
        challengeCommandService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // then
        assertThat(v.isDeleted()).isTrue();
        assertThat(challengeQueryService.getChallenge(challenge.getId(), me.getId())
                .verifiedInCurrentPeriod())
                .as("인증을 취소한 것이 아니므로 그날은 다시 못 한다")
                .isTrue();
    }

    @Test
    @DisplayName("오늘 것을 내려도 스트릭은 그대로다 — 남용은 재화 회수가 막는다")
    void delete_Today_KeepsStreak() {
        // given: 어제·오늘 이어서 인증해 스트릭 2
        verificationOn(today().minusDays(1));
        ChallengeVerification todayOne = verificationOn(today());
        participation.applyVerification(today().minusDays(1));
        participation.applyVerification(today());
        em.flush();

        // when
        challengeCommandService.deleteVerification(me.getId(), challenge.getId(), todayOne.getId());
        em.flush();

        // then — 시간 기준으로 깎으면 그만큼 기다려 우회되고, 정당하게 지우는 사람만 다친다.
        assertThat(participation.getCurrentStreak()).isEqualTo(2);
        assertThat(participation.getLastVerifiedDate()).isEqualTo(today());
    }

    @Test
    @DisplayName("지난 글을 내려도 스트릭은 그대로다")
    void delete_PastVerification_KeepsStreak() {
        // given: 사흘 연속 인증해 스트릭 3
        ChallengeVerification twoDaysAgo = verificationOn(today().minusDays(2));
        verificationOn(today().minusDays(1));
        verificationOn(today());
        participation.applyVerification(today().minusDays(2));
        participation.applyVerification(today().minusDays(1));
        participation.applyVerification(today());
        em.flush();

        // when
        challengeCommandService.deleteVerification(me.getId(), challenge.getId(), twoDaysAgo.getId());
        em.flush();

        // then
        assertThat(participation.getCurrentStreak()).isEqualTo(3);
        assertThat(participation.getLastVerifiedDate()).isEqualTo(today());
    }

    @Test
    @DisplayName("사진도 S3에서 지운다 — 조회에서 빼는 것만으로는 URL 을 아는 사람이 계속 본다")
    void delete_AlsoRemovesPhoto() {
        // given
        ChallengeVerification v = verificationOn(today());

        // when
        challengeCommandService.deleteVerification(me.getId(), challenge.getId(), v.getId());

        // then
        verify(mediaService).deleteQuietly(PUBLIC_KEY);
    }

    @Test
    @DisplayName("이미 내린 글을 다시 지우면 성공이고 사진을 또 지우지 않는다")
    void delete_Twice_IsIdempotent() {
        // given
        ChallengeVerification v = verificationOn(today());
        challengeCommandService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();
        LocalDateTime firstDeletedAt = v.getDeletedAt();

        // when
        challengeCommandService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // then — 시각을 덮어쓰지 않고, 사진 삭제도 한 번뿐이다
        assertThat(v.getDeletedAt()).isEqualTo(firstDeletedAt);
        verify(mediaService).deleteQuietly(PUBLIC_KEY);
    }

    @Test
    @DisplayName("남의 글은 없는 글과 같은 404다 — 존재 여부를 알려 주지 않는다")
    void delete_OthersVerification_Is404() {
        // given
        ChallengeVerification v = verificationOn(today());
        Member other = Member.builder()
                .email("other@ex.com").nickname("other")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("other-sid").build();
        em.persist(other);
        em.flush();

        // when & then
        assertThatThrownBy(() -> challengeCommandService
                .deleteVerification(other.getId(), challenge.getId(), v.getId()))
                .isInstanceOf(RuntimeException.class);
        verify(mediaService, never()).deleteQuietly(any());
    }

    @Test
    @DisplayName("내린 뒤 다시 올리면 그 자리가 되살아난다 — 하루 한 건이라는 성질이 유지된다")
    void delete_ThenVerifyAgain_RevivesSameRow() {
        // given
        ChallengeVerification v = verificationOn(today());
        challengeCommandService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // when — 덮어쓰기 경로가 내린 행을 찾아야 한다. 못 찾으면 INSERT 해서 유니크 제약에 걸린다.
        v.reverify(PUBLIC_KEY, "다시 올림", LocalDateTime.now(),
                v.getReviewStatus(), null);
        em.flush();

        // then
        assertThat(v.isDeleted()).isFalse();
        assertThat(em.createQuery(
                        "select count(v) from ChallengeVerification v where v.memberChallenge.id = :id",
                        Long.class)
                .setParameter("id", participation.getId())
                .getSingleResult())
                .as("행이 늘지 않는다")
                .isEqualTo(1L);
    }
}
