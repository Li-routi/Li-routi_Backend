package com.lirouti.domain.challenge.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.entity.ChallengeVerificationLike;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.global.util.TimeUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 챌린지 대표 이미지.
 *
 * <p>표지는 <b>참여하지 않은 사람에게도 목록에서 보인다.</b> 인증 사진 중에서 고르는 이상,
 * 피드에서 가려 놓은 사진이 여기로 올라오면 가린 의미가 없어진다. 그래서 이 테스트의 무게는
 * "1위가 잘 뽑히나"보다 <b>"빠져야 할 것이 빠지나"</b>에 있다.
 *
 * <p>제외 조건 넷을 각각 따로 본다. 한 테스트에 몰아 넣으면 하나가 빠져도 다른 셋이 걸러 줘서
 * 통과해 버린다.
 */
@SpringBootTest
@Transactional
@DisplayName("챌린지 대표 이미지")
class ChallengeCoverImageTest {

    @Autowired
    private ChallengeQueryService queryService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    // ── 픽스처 ──

    private Member member(boolean active) {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("cover" + n + "@ex.com").nickname("cover" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("cover-sid-" + n).build();
        if (!active) {
            // 다른 집계 테스트와 같은 방식이다. withdraw() 는 익명화 인자를 받아 여기서 쓰기엔
            // 무겁고, 표지 쿼리가 보는 것은 isActive 하나다.
            ReflectionTestUtils.setField(m, "isActive", false);
        }
        em.persist(m);
        return m;
    }

    private Challenge challenge(String operatorCover) {
        Challenge c = Challenge.builder()
                .name("표지챌린지" + seq.incrementAndGet())
                .category(ChallengeCategory.HEALTH)
                .imageUrl(operatorCover)
                .active(true).build();
        em.persist(c);
        return c;
    }

    private Challenge challenge() {
        return challenge(null);
    }

    /** 인증 한 건. 작성자와 참여 행도 함께 만든다. */
    private ChallengeVerification verification(Challenge c, String photo, boolean authorActive) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(member(authorActive)).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        LocalDate today = LocalDate.now(TimeUtil.KST);
        ChallengeVerification v = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(today).periodStartDate(today)
                .verifiedAt(LocalDateTime.now())
                .imageUrl(photo).build();
        em.persist(v);
        return v;
    }

    private ChallengeVerification verification(Challenge c, String photo) {
        return verification(c, photo, true);
    }

    private void like(ChallengeVerification v, int count) {
        for (int i = 0; i < count; i++) {
            em.persist(ChallengeVerificationLike.builder()
                    .challengeVerification(v).member(member(true)).build());
        }
    }

    /** 상세 응답의 표지. 목록과 갈라지지 않는지 볼 때도 쓴다. */
    private String coverOfDetail(Challenge c) {
        return queryService.getChallenge(c.getId(), null).imageUrl();
    }

    /** 찾아보기 목록에서 이 챌린지 카드의 표지. */
    private String coverOfListing(Challenge c) {
        // findFirst() 를 imageUrl 에 걸면 안 된다 — 표지가 없는 챌린지에서 NPE 가 난다.
        // 카드를 먼저 찾고 그 다음에 필드를 꺼낸다.
        return queryService.getChallenges(null, c.getName(), null, 20)
                .challenges().stream()
                .filter(s -> s.challengeId().equals(c.getId()))
                .findFirst().orElseThrow()
                .imageUrl();
    }

    // ── 선정 ──

    @Test
    @DisplayName("좋아요를 가장 많이 받은 인증 사진이 표지가 된다")
    void picksMostLikedPhoto() {
        Challenge c = challenge();
        verification(c, "0개.jpg");
        ChallengeVerification one = verification(c, "1개.jpg");
        ChallengeVerification three = verification(c, "3개.jpg");
        like(one, 1);
        like(three, 3);
        em.flush();
        em.clear();

        assertThat(coverOfDetail(c)).isEqualTo("3개.jpg");
    }

    @Test
    @DisplayName("좋아요가 전부 0이어도 표지가 흔들리지 않는다 — 가장 최근 인증으로 고정")
    void stableWhenNoLikesAtAll() {
        Challenge c = challenge();
        verification(c, "먼저.jpg");
        ChallengeVerification newer = verification(c, "나중.jpg");
        em.flush();
        em.clear();

        // 같은 요청을 여러 번 보내도 같은 사진이어야 한다. tie-break 가 없으면 여기서 갈린다.
        for (int i = 0; i < 3; i++) {
            assertThat(coverOfDetail(c)).isEqualTo("나중.jpg");
        }
        assertThat(newer.getId()).isNotNull();
    }

    @Test
    @DisplayName("인증이 하나도 없으면 null 이다")
    void nullWhenNoVerification() {
        Challenge c = challenge();
        em.flush();
        em.clear();

        assertAll(
                () -> assertThat(coverOfDetail(c)).isNull(),
                () -> assertThat(coverOfListing(c)).isNull()
        );
    }

    @Test
    @DisplayName("목록과 상세가 같은 사진을 준다")
    void listingAndDetailAgree() {
        Challenge c = challenge();
        verification(c, "밀린것.jpg");
        ChallengeVerification top = verification(c, "1위.jpg");
        like(top, 2);
        em.flush();
        em.clear();

        assertThat(coverOfListing(c))
                .as("목록에서 본 카드와 들어가서 본 사진이 다르면 잘못 들어온 줄 안다")
                .isEqualTo(coverOfDetail(c))
                .isEqualTo("1위.jpg");
    }

    // ── 폴백 ──

    @Test
    @DisplayName("운영이 지정한 표지가 있으면 그것이 이긴다")
    void operatorCoverWins() {
        Challenge c = challenge("운영표지.jpg");
        ChallengeVerification top = verification(c, "인기인증.jpg");
        like(top, 5);
        em.flush();
        em.clear();

        assertAll(
                () -> assertThat(coverOfDetail(c)).isEqualTo("운영표지.jpg"),
                () -> assertThat(coverOfListing(c)).isEqualTo("운영표지.jpg")
        );
    }

    // ── 제외 조건 넷 ──

    @Test
    @DisplayName("신고로 가려진 인증은 표지가 되지 않는다")
    void excludesHidden() {
        Challenge c = challenge();
        ChallengeVerification hidden = verification(c, "가려짐.jpg");
        ChallengeVerification plain = verification(c, "정상.jpg");
        like(hidden, 5);
        hidden.hide(LocalDateTime.now());
        em.flush();
        em.clear();

        assertThat(coverOfDetail(c))
                .as("피드에서 가린 사진이 표지로 올라가면 가린 의미가 없다")
                .isEqualTo("정상.jpg");
        assertThat(plain.getId()).isNotNull();
    }

    @Test
    @DisplayName("심사 보류 중인 인증은 표지가 되지 않는다")
    void excludesPending() {
        Challenge c = challenge();
        MemberChallenge mc = MemberChallenge.builder()
                .member(member(true)).challenge(c)
                .participationRound(1).currentStreak(1)
                .joinedAt(LocalDateTime.now()).active(true).build();
        em.persist(mc);
        LocalDate today = LocalDate.now(TimeUtil.KST);
        ChallengeVerification pending = ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(1)
                .verifiedDate(today).periodStartDate(today)
                .verifiedAt(LocalDateTime.now())
                .imageUrl("심사중.jpg")
                .reviewStatus(ReviewStatus.PENDING)
                .pendingSince(LocalDateTime.now()).build();
        em.persist(pending);
        like(pending, 5);
        verification(c, "정상.jpg");
        em.flush();
        em.clear();

        assertThat(coverOfDetail(c))
                .as("심사를 안 지난 사진이 가장 넓게 공개되는 자리로 올라가면 안 된다")
                .isEqualTo("정상.jpg");
    }

    @Test
    @DisplayName("지워진 인증은 표지가 되지 않는다")
    void excludesDeleted() {
        Challenge c = challenge();
        ChallengeVerification removed = verification(c, "지움.jpg");
        verification(c, "정상.jpg");
        like(removed, 5);
        removed.softDelete(LocalDateTime.now());
        em.flush();
        em.clear();

        assertThat(coverOfDetail(c))
                .as("글을 지우면 표지에서도 즉시 내려간다 — 사실상의 거부 수단이다")
                .isEqualTo("정상.jpg");
    }

    @Test
    @DisplayName("탈퇴 회원의 인증은 표지가 되지 않는다")
    void excludesWithdrawnAuthor() {
        Challenge c = challenge();
        ChallengeVerification byWithdrawn = verification(c, "탈퇴자.jpg", false);
        verification(c, "정상.jpg");
        like(byWithdrawn, 5);
        em.flush();
        em.clear();

        assertThat(coverOfDetail(c)).isEqualTo("정상.jpg");
    }

    @Test
    @DisplayName("탈퇴 회원이 누른 좋아요는 순위에 세지 않는다")
    void withdrawnLikesDoNotCount() {
        Challenge c = challenge();
        ChallengeVerification aliveOne = verification(c, "산사람1개.jpg");
        ChallengeVerification withdrawnThree = verification(c, "탈퇴3개.jpg");
        em.persist(ChallengeVerificationLike.builder()
                .challengeVerification(aliveOne).member(member(true)).build());
        for (int i = 0; i < 3; i++) {
            em.persist(ChallengeVerificationLike.builder()
                    .challengeVerification(withdrawnThree).member(member(false)).build());
        }
        em.flush();
        em.clear();

        assertThat(coverOfDetail(c))
                .as("다른 집계와 같은 규칙으로 세야 화면에 뜨는 수와 어긋나지 않는다")
                .isEqualTo("산사람1개.jpg");
    }

    // ── 배치 ──

    @Test
    @DisplayName("여러 챌린지를 한 목록에서 받아도 각자의 1위가 붙는다")
    void batchGivesEachChallengeItsOwnWinner() {
        Challenge a = challenge();
        Challenge b = challenge();
        ChallengeVerification aTop = verification(a, "A1위.jpg");
        verification(a, "A꼴찌.jpg");
        ChallengeVerification bTop = verification(b, "B1위.jpg");
        verification(b, "B꼴찌.jpg");
        like(aTop, 2);
        like(bTop, 2);
        em.flush();
        em.clear();

        List<ChallengeResDTO.Summary> cards = queryService.getChallenges(null, "표지챌린지", null, 50)
                .challenges();

        assertAll(
                () -> assertThat(cards).extracting(ChallengeResDTO.Summary::challengeId)
                        .contains(a.getId(), b.getId()),
                () -> assertThat(coverOfListing(a)).isEqualTo("A1위.jpg"),
                () -> assertThat(coverOfListing(b)).isEqualTo("B1위.jpg")
        );
    }
}
