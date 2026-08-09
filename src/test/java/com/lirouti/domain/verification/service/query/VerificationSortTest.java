package com.lirouti.domain.verification.service.query;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.entity.ChallengeVerificationLike;
import com.lirouti.domain.verification.enums.VerificationSort;
import com.lirouti.global.util.TimeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 인증 목록 정렬.
 *
 * <p>여기서는 <b>정렬 자체의 셈법</b>을 본다. "파라미터를 생략하면 최신순" 이라는 기본값은
 * 컨트롤러의 {@code defaultValue} 가 정하므로 HTTP 계층에서 봐야 한다 —
 * {@code ChallengeVerificationControllerTest} 에 있다. 여기서 서비스에 LATEST 를 직접
 * 넘겨 놓고 "기본값을 검증했다" 고 하면 그 값이 바뀌어도 통과한다.
 *
 * <p>좋아요순은 <b>표시되는 수와 정렬 기준이 같은지</b>를 본다. 둘이 갈리면 "정렬은 5개
 * 기준인데 화면에는 3개로 보이는" 상태가 되는데, 그게 이 기능에서 가장 현실적인 사고다.
 */
@SpringBootTest
@Transactional
@DisplayName("인증 목록 정렬")
class VerificationSortTest {

    private static final String KEY = "challenge-verifications/sort-fixture.jpg";

    @Autowired
    private ChallengeVerificationQueryService queryService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member member(boolean active) {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("sort" + n + "@ex.com").nickname("sort" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("sort-sid-" + n).build();
        if (!active) {
            // 다른 리포지터리 테스트와 같은 방식이다. withdraw() 는 익명화 인자를 받아
            // 여기서 쓰기엔 무겁고, 집계가 보는 것은 isActive 하나다.
            ReflectionTestUtils.setField(m, "isActive", false);
        }
        em.persist(m);
        return m;
    }

    private Challenge challenge() {
        Challenge c = Challenge.builder()
                .name("정렬챌린지").category(ChallengeCategory.HEALTH).active(true).build();
        em.persist(c);
        return c;
    }

    /** 작성자와 인증을 함께 만든다. 인증 하나마다 참여 행이 따로 생긴다. */
    private ChallengeVerification verification(Challenge c, String content) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(member(true)).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        LocalDate today = LocalDate.now(TimeUtil.KST);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(today).periodStartDate(today)
                .verifiedAt(LocalDateTime.now())
                .imageUrl(KEY).content(content).build();
        em.persist(v);
        return v;
    }

    private void like(ChallengeVerification v, Member liker) {
        em.persist(ChallengeVerificationLike.builder()
                .challengeVerification(v).member(liker).build());
    }

    private List<String> contentsOf(ChallengeVerificationResDTO.Feed feed) {
        return feed.verifications().stream()
                .map(ChallengeVerificationResDTO.FeedItem::content)
                .toList();
    }

    @Test
    @DisplayName("LATEST 는 id 내림차순이다 — 좋아요가 많아도 순서를 바꾸지 않는다")
    void latestSort_OrdersByIdDesc() {
        Challenge c = challenge();
        ChallengeVerification first = verification(c, "먼저");
        ChallengeVerification second = verification(c, "나중");
        // 먼저 올린 것에 좋아요를 몰아준다. 최신순이면 순서가 그대로여야 한다.
        like(first, member(true));
        like(first, member(true));
        em.flush();
        em.clear();

        ChallengeVerificationResDTO.Feed feed =
                queryService.getVerificationFeed(c.getId(), null, null, null, null, VerificationSort.LATEST);

        assertThat(contentsOf(feed))
                .as("id 내림차순 — 나중에 올린 것이 위다")
                .containsExactly("나중", "먼저");
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    @DisplayName("좋아요순은 많이 받은 것이 위다")
    void likesSort_OrdersByLikeCount() {
        Challenge c = challenge();
        ChallengeVerification none = verification(c, "0개");
        ChallengeVerification one = verification(c, "1개");
        ChallengeVerification two = verification(c, "2개");
        like(one, member(true));
        like(two, member(true));
        like(two, member(true));
        em.flush();
        em.clear();

        ChallengeVerificationResDTO.Feed feed =
                queryService.getVerificationFeed(c.getId(), null, null, null, null, VerificationSort.LIKES);

        assertThat(contentsOf(feed)).containsExactly("2개", "1개", "0개");
        // 좋아요가 없는 인증도 빠지지 않는다(left join 이라야 한다).
        assertThat(feed.verifications()).hasSize(3);
        assertThat(none.getId()).isNotNull();
    }

    @Test
    @DisplayName("정렬 기준과 화면에 뜨는 수가 같다 — 탈퇴 회원의 좋아요는 양쪽 다 빠진다")
    void likesSort_UsesSameCountAsDisplay() {
        Challenge c = challenge();
        ChallengeVerification aliveOnly = verification(c, "산 사람 1");
        ChallengeVerification withdrawnHeavy = verification(c, "탈퇴 3");

        like(aliveOnly, member(true));
        // 탈퇴 회원이 셋 눌렀다. 세지 않으므로 위로 올라가면 안 된다.
        like(withdrawnHeavy, member(false));
        like(withdrawnHeavy, member(false));
        like(withdrawnHeavy, member(false));
        em.flush();
        em.clear();

        ChallengeVerificationResDTO.Feed feed =
                queryService.getVerificationFeed(c.getId(), null, null, null, null, VerificationSort.LIKES);

        assertAll(
                () -> assertThat(contentsOf(feed))
                        .as("정렬도 탈퇴 회원을 빼고 센다")
                        .containsExactly("산 사람 1", "탈퇴 3"),
                () -> assertThat(feed.verifications().get(0).likeCount()).isEqualTo(1L),
                () -> assertThat(feed.verifications().get(1).likeCount())
                        .as("화면에 뜨는 수도 0 이라야 정렬과 어긋나지 않는다")
                        .isZero()
        );
    }

    @Test
    @DisplayName("좋아요 수가 같으면 최신순으로 가른다 — 순서가 흔들리면 안 된다")
    void likesSort_TieBreaksById() {
        Challenge c = challenge();
        ChallengeVerification older = verification(c, "먼저");
        ChallengeVerification newer = verification(c, "나중");
        like(older, member(true));
        like(newer, member(true));
        em.flush();
        em.clear();

        // 같은 요청을 두 번 보내도 순서가 같아야 한다.
        for (int i = 0; i < 2; i++) {
            ChallengeVerificationResDTO.Feed feed =
                    queryService.getVerificationFeed(c.getId(), null, null, null, null, VerificationSort.LIKES);
            assertThat(contentsOf(feed)).containsExactly("나중", "먼저");
        }
        assertThat(newer.getId()).isGreaterThan(older.getId());
    }

    @Test
    @DisplayName("좋아요순도 커서로 이어진다 — 페이지를 넘겨도 중복·누락이 없다")
    void likesSort_PagesThroughWithCompositeCursor() {
        Challenge c = challenge();
        // 좋아요 수가 겹치게 만든다. 0개가 셋이라 id 없이는 좌표가 안 잡힌다.
        ChallengeVerification two = verification(c, "2개");
        ChallengeVerification oneA = verification(c, "1개A");
        ChallengeVerification oneB = verification(c, "1개B");
        ChallengeVerification zeroA = verification(c, "0개A");
        ChallengeVerification zeroB = verification(c, "0개B");
        like(two, member(true));
        like(two, member(true));
        like(oneA, member(true));
        like(oneB, member(true));
        em.flush();
        em.clear();

        // 2개씩 끝까지 넘긴다.
        List<String> seen = new java.util.ArrayList<>();
        Long cursor = null;
        Long cursorLikes = null;
        for (int page = 0; page < 5; page++) {
            ChallengeVerificationResDTO.Feed feed = queryService.getVerificationFeed(
                    c.getId(), null, cursor, cursorLikes, 2, VerificationSort.LIKES);
            seen.addAll(contentsOf(feed));
            if (!feed.hasNext()) {
                break;
            }
            cursor = feed.nextCursor();
            cursorLikes = feed.nextCursorLikeCount();
            assertThat(cursorLikes).as("좋아요순이면 두 번째 커서 값이 있어야 한다").isNotNull();
        }

        // 다섯 건이 정확히 한 번씩. 같은 수는 id 내림차순(나중에 만든 것이 위).
        assertThat(seen).containsExactly("2개", "1개B", "1개A", "0개B", "0개A");
        assertThat(oneB.getId()).isGreaterThan(oneA.getId());
        assertThat(zeroB.getId()).isGreaterThan(zeroA.getId());
    }

    @Test
    @DisplayName("최신순에는 두 번째 커서 값이 안 실린다")
    void latestSort_HasNoLikeCursor() {
        Challenge c = challenge();
        verification(c, "하나");
        verification(c, "둘");
        em.flush();
        em.clear();

        ChallengeVerificationResDTO.Feed feed = queryService.getVerificationFeed(
                c.getId(), null, null, null, 1, VerificationSort.LATEST);

        assertAll(
                () -> assertThat(feed.hasNext()).isTrue(),
                () -> assertThat(feed.nextCursor()).isNotNull(),
                () -> assertThat(feed.nextCursorLikeCount())
                        .as("최신순은 id 하나로 좌표가 잡힌다").isNull()
        );
    }
}
