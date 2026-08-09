package com.lirouti.domain.verification.service.query;

import com.lirouti.domain.verification.enums.VerificationSort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 피드의 {@code mine} — <b>내 글과 남의 글을 가르는 값이다.</b>
 *
 * <p>클라이언트가 이 값으로 삭제·신고 버튼을 그린다. 잘못 내려가면 남의 글에 내 버튼이 붙는다.
 * 그래서 "내 것이 true 인가"만큼이나 <b>"남의 것이 false 인가"</b>를 본다.
 *
 * <p>닉네임으로는 대신할 수 없다는 것도 함께 확인한다 — 닉네임에 유니크 제약이 없어
 * 동명이인이 실제로 만들어지고, 그때 이름 비교는 틀린 답을 준다.
 */
@SpringBootTest
@Transactional
@DisplayName("인증 피드 mine 플래그 테스트")
class FeedMineFlagTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY =
            "challenge-verifications/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";

    @Autowired
    private ChallengeVerificationQueryService challengeVerificationQueryService;

    @PersistenceContext
    private EntityManager em;

    // ── 픽스처 ──
    private Member persistMember(String tag, String nickname) {
        Member m = Member.builder()
                .email(tag + "@ex.com").nickname(nickname)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag + "-sid").build();
        em.persist(m);
        return m;
    }

    private Challenge persistChallenge() {
        Challenge c = Challenge.builder()
                .name("mine챌린지").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    private ChallengeVerification persistVerification(Member author, Challenge c, String content) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(author).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        // now() 를 한 번만 부른다. 두 번 부르면 KST 자정 경계에서 verifiedDate 와
        // periodStartDate 가 다른 날을 가리켜 테스트가 그때만 깨진다.
        LocalDate today = LocalDate.now(KST);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(today)
                .periodStartDate(today).verifiedAt(LocalDateTime.now())
                .imageUrl(KEY).content(content).build();
        em.persist(v);
        em.flush();
        return v;
    }

    /** 인증 id → 그 카드의 mine 값. */
    private Map<Long, Boolean> mineByVerificationId(Long challengeId, Long viewerId) {
        ChallengeVerificationResDTO.Feed feed =
                challengeVerificationQueryService.getVerificationFeed(challengeId, viewerId, null, null, VerificationSort.LATEST);
        return feed.verifications().stream().collect(Collectors.toMap(
                ChallengeVerificationResDTO.FeedItem::verificationId,
                ChallengeVerificationResDTO.FeedItem::mine,
                (a, b) -> a));
    }

    // ── 테스트 ──

    @Test
    @DisplayName("내 인증만 mine=true다 — 같은 피드의 남의 인증은 false다")
    void feed_MarksOnlyOwnVerification() {
        // given: 한 챌린지에 나와 남이 각각 인증한다
        Challenge challenge = persistChallenge();
        Member me = persistMember("mine-me", "나");
        Member other = persistMember("mine-other", "남");
        ChallengeVerification myVerification = persistVerification(me, challenge, "내 것");
        ChallengeVerification othersVerification = persistVerification(other, challenge, "남의 것");
        em.clear();

        // when
        Map<Long, Boolean> mine = mineByVerificationId(challenge.getId(), me.getId());

        // then
        assertAll(
                () -> assertThat(mine).hasSize(2),
                () -> assertThat(mine.get(myVerification.getId())).isTrue(),
                () -> assertThat(mine.get(othersVerification.getId())).isFalse()
        );
    }

    @Test
    @DisplayName("조회자가 바뀌면 같은 인증의 mine도 뒤집힌다 — 인증이 아니라 보는 사람에 달렸다")
    void feed_MineDependsOnViewer() {
        // given
        Challenge challenge = persistChallenge();
        Member author = persistMember("mine-author", "작성자");
        Member viewer = persistMember("mine-viewer", "조회자");
        ChallengeVerification verification = persistVerification(author, challenge, "한 건");
        em.clear();

        // when: 같은 인증을 두 사람 시점으로 각각 본다
        Boolean asAuthor = mineByVerificationId(challenge.getId(), author.getId())
                .get(verification.getId());
        Boolean asViewer = mineByVerificationId(challenge.getId(), viewer.getId())
                .get(verification.getId());

        // then
        assertAll(
                () -> assertThat(asAuthor).isTrue(),
                () -> assertThat(asViewer).isFalse()
        );
    }

    @Test
    @DisplayName("닉네임이 같아도 남의 글은 false다 — 닉네임에 유니크 제약이 없다")
    void feed_SameNickname_IsNotMistakenForMine() {
        // given: 닉네임이 똑같은 두 회원. DB가 막지 않으므로 실제로 만들어진다
        Challenge challenge = persistChallenge();
        Member me = persistMember("mine-dup-a", "김철수");
        Member impostor = persistMember("mine-dup-b", "김철수");
        ChallengeVerification myVerification = persistVerification(me, challenge, "내 것");
        ChallengeVerification othersVerification = persistVerification(impostor, challenge, "동명이인");
        em.clear();

        // when
        ChallengeVerificationResDTO.Feed feed =
                challengeVerificationQueryService.getVerificationFeed(challenge.getId(), me.getId(), null, null, VerificationSort.LATEST);
        Map<Long, ChallengeVerificationResDTO.FeedItem> byId = feed.verifications().stream()
                .collect(Collectors.toMap(ChallengeVerificationResDTO.FeedItem::verificationId,
                        Function.identity()));

        // then: 닉네임은 구분이 안 되지만 mine 은 갈린다
        assertAll(
                () -> assertThat(byId.get(myVerification.getId()).nickname())
                        .isEqualTo(byId.get(othersVerification.getId()).nickname()),
                () -> assertThat(byId.get(myVerification.getId()).mine()).isTrue(),
                () -> assertThat(byId.get(othersVerification.getId()).mine()).isFalse()
        );
    }
}
