package com.lirouti.domain.verification.service;

import com.lirouti.domain.verification.enums.ReportType;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.repository.ChallengeVerificationLikeRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
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
    private static final String NEW_STAGING_KEY =
            "challenge-verifications-staging/2026/08/09/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";
    private static final String NEW_PUBLIC_KEY =
            "challenge-verifications/2026/08/09/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";

    @Autowired
    private ChallengeVerificationService challengeVerificationService;

    @Autowired
    private ChallengeQueryService challengeQueryService;

    @MockitoBean
    private MediaService mediaService;

    @Autowired
    private ChallengeVerificationRepository challengeVerificationRepository;

    @Autowired
    private ChallengeVerificationLikeRepository challengeVerificationLikeRepository;

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
        // 다시 올리는 경로용. 심사용 사진 읽기는 실패로 두면 심사를 건너뛰고 통과한다.
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());
        when(mediaService.loadForReview(any(), anyInt())).thenReturn(MediaImageLoad.readFailed());
        when(mediaService.promote(any(), any(), any())).thenReturn(NEW_PUBLIC_KEY);
    }

    private LocalDate today() {
        return LocalDate.now(TimeUtil.KST);
    }

    /** 지정한 날짜의 인증을 만든다. 스트릭은 호출부가 직접 맞춘다. */
    private ChallengeVerification verificationOn(LocalDate date) {
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(participation).participationRound(1)
                .verifiedDate(date)
                .periodStartDate(date).verifiedAt(date.atTime(10, 0))
                .imageUrl(PUBLIC_KEY).content("인증")
                .build();
        em.persist(v);
        em.flush();
        return v;
    }

    @Test
    @DisplayName("글을 내리면 그 구간이 다시 열린다 — 버튼도 함께 열려야 한다")
    void delete_ReopensThePeriod() {
        // given
        ChallengeVerification v = verificationOn(today());
        participation.applyVerification(today(), RoutineCycle.DAILY);
        em.flush();

        // when
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // then
        assertThat(v.isDeleted()).isTrue();
        assertThat(challengeQueryService.getChallenge(challenge.getId(), me.getId())
                .verifiedInCurrentPeriod())
                .as("서버가 재인증을 받아 주는데 버튼이 잠긴 채면 사용자가 거기 닿을 수 없다")
                .isFalse();
    }

    @Test
    @DisplayName("오늘 것을 내려도 스트릭은 그대로다 — 남용은 재화 회수가 막는다")
    void delete_Today_KeepsStreak() {
        // given: 어제·오늘 이어서 인증해 스트릭 2
        verificationOn(today().minusDays(1));
        ChallengeVerification todayOne = verificationOn(today());
        participation.applyVerification(today().minusDays(1), RoutineCycle.DAILY);
        participation.applyVerification(today(), RoutineCycle.DAILY);
        em.flush();

        // when
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), todayOne.getId());
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
        participation.applyVerification(today().minusDays(2), RoutineCycle.DAILY);
        participation.applyVerification(today().minusDays(1), RoutineCycle.DAILY);
        participation.applyVerification(today(), RoutineCycle.DAILY);
        em.flush();

        // when
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), twoDaysAgo.getId());
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
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());

        // then
        verify(mediaService).deleteQuietly(PUBLIC_KEY);
    }

    @Test
    @DisplayName("이미 내린 글을 다시 지우면 성공이고 사진을 또 지우지 않는다")
    void delete_Twice_IsIdempotent() {
        // given
        ChallengeVerification v = verificationOn(today());
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();
        LocalDateTime firstDeletedAt = v.getDeletedAt();

        // when
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
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
        assertThatThrownBy(() -> challengeVerificationService
                .deleteVerification(other.getId(), challenge.getId(), v.getId()))
                .isInstanceOf(RuntimeException.class);
        verify(mediaService, never()).deleteQuietly(any());
    }

    @Test
    @DisplayName("내려간 글에는 좋아요·신고·메모 수정이 안 된다 — 피드에 없는 글이다")
    void deletedVerification_RejectsOtherActions() {
        // given
        ChallengeVerification v = verificationOn(today());
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // when & then
        assertThatThrownBy(() -> challengeVerificationService.like(me.getId(), challenge.getId(), v.getId()))
                .as("되살아났을 때 엉뚱한 좋아요 수를 달고 나타나면 안 된다")
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> challengeVerificationService.report(me.getId(), challenge.getId(), v.getId(),
                new ChallengeVerificationReqDTO.Report(ReportType.IRRELEVANT, null)))
                .as("이미 내려간 글로 숨김 임계값을 채우면 안 된다")
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> challengeVerificationService.updateMemo(me.getId(), challenge.getId(), v.getId(),
                new ChallengeVerificationReqDTO.UpdateMemo("고침")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("내려간 글의 사진은 미참조 정리가 가져간다 — S3 삭제가 실패해도 남지 않게")
    void deletedVerification_IsNotCountedAsReference() {
        // given
        ChallengeVerification v = verificationOn(today());
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();
        em.clear();

        // when — 미참조 정리가 "살아 있는 사진" 을 가릴 때 쓰는 조회
        List<String> referenced = challengeVerificationRepository.findImageUrlsIn(List.of(PUBLIC_KEY));

        // then — 참조로 세면 정리 배치가 못 가져가 지운 사진이 영영 남는다
        assertThat(referenced).isEmpty();
    }

    @Test
    @DisplayName("지웠다 다시 올린 뒤 또 지우면 현재 사진을 지운다 — 옛 key 만 지우면 사진이 남는다")
    void delete_PhotoReplacedJustBefore_RemovesCurrentPhoto() {
        // given: 인증 → 삭제 → 재인증(사진이 갈린다). 덮어쓰기가 없어져 이 경로로만 갈린다.
        ChallengeVerification v = verificationOn(today());
        participation.applyVerification(today(), RoutineCycle.DAILY);
        em.flush();

        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        challengeVerificationService.verify(me.getId(), challenge.getId(),
                new ChallengeVerificationReqDTO.Verify(NEW_STAGING_KEY, "다시 올림"));
        em.flush();
        assertThat(v.getImageUrl()).isEqualTo(NEW_PUBLIC_KEY);
        assertThat(v.getDeletedAt()).as("되살아났다").isNull();

        // when
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());

        // then — 잠그고 다시 읽지 않으면 옛 key 를 지워, 현재 사진이 공개 prefix 에 남는다.
        verify(mediaService).deleteQuietly(NEW_PUBLIC_KEY);
    }

    @Test
    @DisplayName("내린 뒤 다시 올리면 그 자리가 되살아난다 — 저장 경로가 내린 행을 찾아야 한다")
    void delete_ThenVerifyAgain_RevivesSameRow() {
        // given: 오늘 인증했다가 내렸다
        ChallengeVerification v = verificationOn(today());
        participation.applyVerification(today(), RoutineCycle.DAILY);
        em.flush();
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // when: 실제 인증 경로로 다시 올린다.
        // 저장 경로가 "오늘 인증 찾기" 에서 내린 행을 못 찾으면 새로 INSERT 하고
        // 유니크 제약에 걸린다 — 소프트 삭제가 성립하는지를 여기서 본다.
        challengeVerificationService.verify(me.getId(), challenge.getId(),
                new ChallengeVerificationReqDTO.Verify(NEW_STAGING_KEY, "다시 올림"));
        em.flush();

        // then
        assertThat(v.isDeleted()).as("되살아난다").isFalse();
        assertThat(v.getImageUrl()).as("새 사진으로 덮인다").isEqualTo(NEW_PUBLIC_KEY);
        assertThat(em.createQuery(
                        "select count(v) from ChallengeVerification v where v.memberChallenge.id = :id",
                        Long.class)
                .setParameter("id", participation.getId())
                .getSingleResult())
                .as("행이 늘지 않는다 — 하루 한 건이 유지된다")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("신고로 가려진 글은 지울 수도 다시 낼 수도 없다 — 그 구간은 닫힌 채로 끝난다")
    void hidden_ClosesThePeriod() {
        // given: 오늘 인증이 신고 누적으로 가려졌다
        ChallengeVerification v = verificationOn(today());
        participation.applyVerification(today(), RoutineCycle.DAILY);
        v.hide(LocalDateTime.now(TimeUtil.KST));
        em.flush();

        // 지울 수 없다 — 지워서 신고 누적을 회피하는 길을 막는다
        assertThatThrownBy(() ->
                challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code",
                        ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND);

        // 다시 낼 수도 없다 — 지우지 않은 인증은 구간을 점유한다
        assertThatThrownBy(() -> challengeVerificationService.verify(me.getId(), challenge.getId(),
                new ChallengeVerificationReqDTO.Verify(NEW_STAGING_KEY, "다시 올림")))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code",
                        ChallengeVerificationErrorCode.ALREADY_VERIFIED_TODAY);

        // 가려짐은 제재이므로 그 구간을 소진한 것으로 본다. 다시 열어 주면 신고를 받은
        // 사람이 사진만 바꿔 계속 낼 수 있어 숨김이 힘을 잃는다.
        assertThat(v.isDeleted()).isFalse();
        assertThat(v.getImageUrl()).as("사진도 그대로다").isEqualTo(PUBLIC_KEY);
    }


    /**
     * <b>인증 삭제는 소프트 삭제라 행이 남고, 다시 올리면 같은 행이 되살아난다.</b> 좋아요는 그
     * 행을 가리키므로 함께 지우지 않으면 <b>새로 올린 사진이 지운 사진의 좋아요를 물려받는다.</b>
     *
     * <p>같은 자리에서 심사 결과와 시도 횟수는 이미 초기화한다 — "사진이 바뀌었으니 지난 심사
     * 결과는 이 사진의 것이 아니다" 가 이유다. 좋아요도 같은 이유로 남으면 안 된다.
     */
    @Test
    @DisplayName("지우면 좋아요도 사라진다 — 다시 올려도 옛 좋아요가 따라오지 않는다")
    void delete_RemovesLikes_AndTheyDoNotComeBackOnReverify() {
        // given: 남이 내 인증에 좋아요를 눌렀다
        ChallengeVerification v = verificationOn(today());
        participation.applyVerification(today(), RoutineCycle.DAILY);
        em.flush();

        Member liker = liker();
        challengeVerificationService.like(liker.getId(), challenge.getId(), v.getId());
        em.flush();
        assertThat(likeCount(v)).as("눌린 상태로 시작한다").isEqualTo(1);

        // when: 인증을 지운다
        challengeVerificationService.deleteVerification(me.getId(), challenge.getId(), v.getId());
        em.flush();

        // then
        assertThat(likeCount(v)).as("지우면 좋아요도 함께 사라진다").isZero();

        // when: 같은 회차에 다시 올린다 — 같은 행이 되살아나는 경로다
        challengeVerificationService.verify(me.getId(), challenge.getId(),
                new ChallengeVerificationReqDTO.Verify(NEW_STAGING_KEY, "다시 올림"));
        em.flush();

        // then
        assertAll(
                () -> assertThat(v.isDeleted()).as("되살아났다").isFalse(),
                () -> assertThat(likeCount(v))
                        .as("새 사진이 옛 좋아요를 물려받지 않는다").isZero());
    }

    private long likeCount(ChallengeVerification verification) {
        em.flush();
        em.clear();
        return em.createQuery("""
                        select count(l) from ChallengeVerificationLike l
                         where l.challengeVerification.id = :id
                        """, Long.class)
                .setParameter("id", verification.getId())
                .getSingleResult();
    }

    private Member liker() {
        int n = seq.incrementAndGet();
        Member member = Member.builder()
                .email("liker" + n + "@ex.com").nickname("liker" + n)
                .socialProvider(me.getSocialProvider()).role(me.getRole())
                .socialId("liker-sid-" + n).build();
        em.persist(member);
        em.flush();
        return member;
    }
}
