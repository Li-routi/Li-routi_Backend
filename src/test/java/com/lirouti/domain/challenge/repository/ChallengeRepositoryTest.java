package com.lirouti.domain.challenge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
@DisplayName("ChallengeRepository QueryDSL 테스트")
class ChallengeRepositoryTest {

    @Autowired
    private ChallengeRepository challengeRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    // 큰 limit — 페이지네이션이 아니라 필터/정렬 로직을 볼 때
    private static final int BIG = 100;

    // ── 헬퍼 ──
    private Member member(boolean active) {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("cq-m" + n + "@ex.com")
                .nickname("m" + n)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("cq-sid-" + n)
                .build();
        if (!active) {
            ReflectionTestUtils.setField(m, "isActive", false);
        }
        em.persist(m);
        return m;
    }

    private Member softDeletedMember() {
        Member m = member(true);
        ReflectionTestUtils.setField(m, "deletedAt", LocalDateTime.now());
        return m;
    }

    private Challenge challenge(String name, ChallengeCategory category, boolean active) {
        Challenge c = Challenge.builder().name(name).category(category).active(active).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge join(Member m, Challenge c, boolean active, int round) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(round).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(active)
                .build();
        em.persist(mc);
        return mc;
    }

    private void verify(MemberChallenge mc, int round, LocalDate date) {
        em.persist(ChallengeVerification.builder()
                .memberChallenge(mc).participationRound(round)
                .verifiedDate(date).verifiedAt(LocalDateTime.now())
                .imageUrl("https://img/x.jpg").build());
    }

    // 커서 없이(첫 페이지) 큰 limit으로 조회 — 필터 로직을 볼 때
    private List<Challenge> find(ChallengeCategory category, String keyword) {
        return challengeRepository.findByCursor(category, keyword, null, BIG);
    }

    @Test
    @DisplayName("최신순: id가 큰(나중에 만든) 챌린지가 앞에 온다")
    void findByCursor_OrdersByLatest() {
        Challenge older = challenge("cq먼저", ChallengeCategory.HEALTH, true);
        Challenge newer = challenge("cq나중", ChallengeCategory.HEALTH, true);
        em.flush();
        em.clear();

        List<Challenge> result = find(null, "cq");

        assertThat(result).extracting(Challenge::getName)
                .containsExactly("cq나중", "cq먼저");
        assertThat(newer.getId()).isGreaterThan(older.getId());
    }

    @Test
    @DisplayName("참여자가 한 명도 없는 활성 챌린지도 목록에 나온다")
    void findByCursor_ZeroParticipantActiveChallenge_Appears() {
        challenge("cq아무도없음", ChallengeCategory.LIFE, true);
        em.flush();
        em.clear();

        assertThat(find(null, "cq아무도없음")).hasSize(1);
    }

    @Test
    @DisplayName("카테고리 필터와 이름 검색이 동작한다")
    void findByCursor_CategoryAndKeyword() {
        challenge("cq운동아침", ChallengeCategory.EXERCISE, true);
        challenge("cq운동저녁", ChallengeCategory.EXERCISE, true);
        challenge("cq독서모임", ChallengeCategory.STUDY, true);
        em.flush();
        em.clear();

        assertThat(find(ChallengeCategory.EXERCISE, "cq")).hasSize(2);
        assertThat(find(null, "운동")).hasSize(2);
        assertThat(find(ChallengeCategory.STUDY, "독서")).hasSize(1);
    }

    @Test
    @DisplayName("비활성 챌린지는 목록·상세에서 제외된다")
    void inactiveChallenge_Excluded() {
        Challenge inactive = challenge("cq숨긴 챌린지", ChallengeCategory.LIFE, false);
        em.flush();
        em.clear();

        assertThat(find(null, "cq숨긴")).isEmpty();
        assertThat(challengeRepository.findByIdAndActiveTrue(inactive.getId())).isEmpty();
    }

    @Test
    @DisplayName("커서 페이지네이션: cursor보다 작은 id만, 최신순으로 limit만큼 가져온다")
    void findByCursor_Pagination() {
        // cqp0 ~ cqp4 (생성 순서 = id 오름차순). 최신순이면 id 큰 것이 앞.
        List<Challenge> made = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            made.add(challenge("cqp" + i, ChallengeCategory.HOBBY, true));
        }
        em.flush();
        em.clear();

        // 첫 페이지: 커서 없이 2건 → 가장 최신 cqp4, cqp3
        List<Challenge> first = challengeRepository.findByCursor(
                ChallengeCategory.HOBBY, "cqp", null, 2);
        assertThat(first).extracting(Challenge::getName).containsExactly("cqp4", "cqp3");

        // 다음 페이지: 커서 = cqp3의 id → 그보다 이전(작은 id)인 cqp2, cqp1
        Long cursor = first.get(1).getId();
        List<Challenge> second = challengeRepository.findByCursor(
                ChallengeCategory.HOBBY, "cqp", cursor, 2);
        assertThat(second).extracting(Challenge::getName).containsExactly("cqp2", "cqp1");
    }

    @Test
    @DisplayName("참여자 수는 이탈(active=false)·비활성 회원·소프트 삭제 회원을 제외한다")
    void countActiveParticipants_ExcludesInactiveAndWithdrawn() {
        Challenge c = challenge("cq물 마시기", ChallengeCategory.HEALTH, true);
        join(member(true), c, true, 1);
        join(member(true), c, false, 1);
        join(member(false), c, true, 1);
        join(softDeletedMember(), c, true, 1);
        em.flush();
        em.clear();

        assertThat(challengeRepository.countActiveParticipants(c.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("오늘 완료자 수: 같은 회원의 회차 다른 오늘 인증 2건을 1명으로 센다")
    void countTodayCompletions_DedupByMemberChallengeAndCurrentRound() {
        Challenge c = challenge("cq스쿼트", ChallengeCategory.EXERCISE, true);
        Member m = member(true);
        MemberChallenge mc = join(m, c, true, 2);
        verify(mc, 1, LocalDate.now());
        verify(mc, 2, LocalDate.now());

        MemberChallenge mc2 = join(member(true), c, true, 1);
        verify(mc2, 1, LocalDate.now());
        em.flush();
        em.clear();

        assertThat(challengeRepository.countTodayCompletions(c.getId(), LocalDate.now())).isEqualTo(2);
    }

    @Test
    @DisplayName("오늘 완료자 수: 어제 인증은 세지 않는다")
    void countTodayCompletions_ExcludesYesterday() {
        Challenge c = challenge("cq독서2", ChallengeCategory.STUDY, true);
        MemberChallenge mc = join(member(true), c, true, 1);
        verify(mc, 1, LocalDate.now().minusDays(1));
        em.flush();
        em.clear();

        assertThat(challengeRepository.countTodayCompletions(c.getId(), LocalDate.now())).isZero();
    }

    @Test
    @DisplayName("오늘 완료자 수: 오늘 인증한 뒤 그만둔(active=false) 회원도 센다 (스키마 규칙)")
    void countTodayCompletions_IncludesQuitterWhoVerifiedToday() {
        Challenge c = challenge("cq플랭크", ChallengeCategory.EXERCISE, true);
        MemberChallenge mc = join(member(true), c, false, 1);
        verify(mc, 1, LocalDate.now());
        em.flush();
        em.clear();

        assertThat(challengeRepository.countTodayCompletions(c.getId(), LocalDate.now())).isEqualTo(1);
    }

    @Test
    @DisplayName("배치 참여자 수: 챌린지별로 활성 참여자를 세고 이탈·탈퇴 회원은 제외한다")
    void countActiveParticipantsByChallengeIds_PerChallenge() {
        Challenge a = challenge("cq배치A", ChallengeCategory.HEALTH, true);
        Challenge b = challenge("cq배치B", ChallengeCategory.HEALTH, true);
        join(member(true), a, true, 1);
        join(member(true), a, true, 1);
        join(member(true), a, false, 1);        // 이탈 제외
        join(softDeletedMember(), a, true, 1);  // 탈퇴 제외
        join(member(true), b, true, 1);
        em.flush();
        em.clear();

        Map<Long, Long> counts = challengeRepository
                .countActiveParticipantsByChallengeIds(List.of(a.getId(), b.getId()));

        assertThat(counts).containsEntry(a.getId(), 2L).containsEntry(b.getId(), 1L);
    }

    @Test
    @DisplayName("배치 인증 게시글 수: 회차 중복을 제거하지 않고(게시글 단위) 세되 탈퇴 회원 인증은 제외한다")
    void countVerificationPostsByChallengeIds_CountsPostsNotPeople() {
        Challenge a = challenge("cq게시글수", ChallengeCategory.EXERCISE, true);
        MemberChallenge mc = join(member(true), a, true, 2);
        verify(mc, 1, LocalDate.now());  // 같은 사람의 회차 다른 인증 2건 → 게시글은 2로 센다
        verify(mc, 2, LocalDate.now());
        MemberChallenge withdrawn = join(softDeletedMember(), a, true, 1);
        verify(withdrawn, 1, LocalDate.now());  // 탈퇴 회원 인증 제외
        em.flush();
        em.clear();

        Map<Long, Long> counts = challengeRepository
                .countVerificationPostsByChallengeIds(List.of(a.getId()));

        assertThat(counts).containsEntry(a.getId(), 2L);
    }

    @Test
    @DisplayName("배치 집계: 빈 id 목록이면 빈 맵을 돌려준다")
    void batchCounts_EmptyIds() {
        assertThat(challengeRepository.countActiveParticipantsByChallengeIds(List.of())).isEmpty();
        assertThat(challengeRepository.countVerificationPostsByChallengeIds(List.of())).isEmpty();
    }
}
