package com.lirouti.domain.challenge.service.command;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.verification.repository.ChallengeVerificationLikeRepository;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;

@SpringBootTest
@Transactional
@DisplayName("인증 게시물 좋아요(#63) 테스트")
class ChallengeLikeTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY =
            "challenge-verifications/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";

    @Autowired
    private ChallengeCommandService challengeCommandService;
    @Autowired
    private ChallengeQueryService challengeQueryService;
    @Autowired
    private ChallengeVerificationLikeRepository likeRepository;

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
                .name("좋아요챌린지").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private ChallengeVerification persistVerification(Member author, Challenge c) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(author).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(LocalDate.now(KST)).verifiedAt(LocalDateTime.now())
                .imageUrl(KEY).content("좋아요 대상").build();
        em.persist(v);
        em.flush();
        return v;
    }

    @Test
    @DisplayName("좋아요를 누르면 수가 오르고 liked=true를 돌려준다")
    void like_IncrementsCount() {
        Member author = persistMember("like-a");
        Member liker = persistMember("like-b");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        ChallengeVerificationResDTO.Like result =
                challengeCommandService.like(liker.getId(), c.getId(), v.getId());

        assertThat(result.verificationId()).isEqualTo(v.getId());
        assertThat(result.likeCount()).isEqualTo(1);
        assertThat(result.liked()).isTrue();
    }

    @Test
    @DisplayName("이미 눌러둔 상태에서 다시 눌러도 성공이고 수가 늘지 않는다 — 멱등")
    void like_Twice_IsIdempotent() {
        Member author = persistMember("like-c");
        Member liker = persistMember("like-d");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        challengeCommandService.like(liker.getId(), c.getId(), v.getId());
        ChallengeVerificationResDTO.Like second =
                challengeCommandService.like(liker.getId(), c.getId(), v.getId());

        assertThat(second.likeCount()).isEqualTo(1);
        assertThat(second.liked()).isTrue();
        assertThat(likeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("취소하면 수가 줄고 liked=false를 돌려준다")
    void unlike_DecrementsCount() {
        Member author = persistMember("like-e");
        Member liker = persistMember("like-f");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        challengeCommandService.like(liker.getId(), c.getId(), v.getId());
        ChallengeVerificationResDTO.Like result =
                challengeCommandService.unlike(liker.getId(), c.getId(), v.getId());

        assertThat(result.likeCount()).isZero();
        assertThat(result.liked()).isFalse();
    }

    @Test
    @DisplayName("누르지 않은 상태에서 취소해도 성공이다 — 멱등")
    void unlike_WithoutLike_IsIdempotent() {
        Member author = persistMember("like-g");
        Member other = persistMember("like-h");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        ChallengeVerificationResDTO.Like result =
                challengeCommandService.unlike(other.getId(), c.getId(), v.getId());

        assertThat(result.likeCount()).isZero();
        assertThat(result.liked()).isFalse();
    }

    @Test
    @DisplayName("취소 후 다시 누를 수 있다 — 하드 삭제라 유니크 제약에 걸리지 않는다")
    void relike_AfterUnlike_Works() {
        Member author = persistMember("like-i");
        Member liker = persistMember("like-j");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        challengeCommandService.like(liker.getId(), c.getId(), v.getId());
        challengeCommandService.unlike(liker.getId(), c.getId(), v.getId());
        ChallengeVerificationResDTO.Like again =
                challengeCommandService.like(liker.getId(), c.getId(), v.getId());

        assertThat(again.likeCount()).isEqualTo(1);
        assertThat(again.liked()).isTrue();
    }

    @Test
    @DisplayName("자기 인증에도 좋아요를 누를 수 있다")
    void like_OwnVerification_IsAllowed() {
        Member author = persistMember("like-k");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        ChallengeVerificationResDTO.Like result =
                challengeCommandService.like(author.getId(), c.getId(), v.getId());

        assertThat(result.likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("그 챌린지에 없는 인증이면 404 — 취소도 마찬가지")
    void like_VerificationNotInChallenge_Throws404() {
        Member author = persistMember("like-l");
        Challenge c = persistChallenge();
        Challenge other = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        assertThatThrownBy(() ->
                challengeCommandService.like(author.getId(), other.getId(), v.getId()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND);

        assertThatThrownBy(() ->
                challengeCommandService.unlike(author.getId(), other.getId(), v.getId()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("피드에 좋아요 수와 내가 눌렀는지가 실린다")
    void feed_CarriesLikeCountAndLiked() {
        Member author = persistMember("like-m");
        Member liker = persistMember("like-n");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        challengeCommandService.like(liker.getId(), c.getId(), v.getId());

        // 누른 사람에게는 liked=true
        ChallengeVerificationResDTO.FeedItem forLiker = challengeQueryService
                .getVerificationFeed(c.getId(), liker.getId(), null, null)
                .verifications().get(0);
        assertThat(forLiker.likeCount()).isEqualTo(1);
        assertThat(forLiker.liked()).isTrue();

        // 안 누른 사람에게는 수만 보이고 liked=false
        ChallengeVerificationResDTO.FeedItem forAuthor = challengeQueryService
                .getVerificationFeed(c.getId(), author.getId(), null, null)
                .verifications().get(0);
        assertThat(forAuthor.likeCount()).isEqualTo(1);
        assertThat(forAuthor.liked()).isFalse();
    }

    @Test
    @DisplayName("내 인증 목록에도 같은 좋아요 수가 나온다 — 같은 인증이므로")
    void myVerifications_CarriesSameLikeCount() {
        Member author = persistMember("like-o");
        Member liker = persistMember("like-p");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        challengeCommandService.like(liker.getId(), c.getId(), v.getId());

        long feedCount = challengeQueryService
                .getVerificationFeed(c.getId(), author.getId(), null, null)
                .verifications().get(0).likeCount();
        long mineCount = challengeQueryService
                .getMyVerifications(author.getId(), c.getId(), null, null)
                .verifications().get(0).likeCount();

        assertThat(mineCount).isEqualTo(feedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 회원의 좋아요는 집계에서 빠진다")
    void likeCount_ExcludesWithdrawnMember() {
        Member author = persistMember("like-q");
        Member leaver = persistMember("like-r");
        Challenge c = persistChallenge();
        ChallengeVerification v = persistVerification(author, c);

        challengeCommandService.like(leaver.getId(), c.getId(), v.getId());
        assertThat(challengeCommandService.like(leaver.getId(), c.getId(), v.getId()).likeCount())
                .isEqualTo(1);

        // 좋아요 취소가 JPQL 벌크 삭제라 영속성 컨텍스트를 비운다(@Modifying clearAutomatically).
        // 앞선 호출로 leaver가 detach됐을 수 있으므로 다시 읽어와 변경해야 UPDATE가 나간다.
        em.find(Member.class, leaver.getId())
                .withdraw("withdrawn@deleted.invalid", "withdrawn-sid", LocalDateTime.now());
        em.flush();
        em.clear();

        long count = challengeQueryService
                .getVerificationFeed(c.getId(), author.getId(), null, null)
                .verifications().get(0).likeCount();

        assertThat(count).isZero();
    }
}
