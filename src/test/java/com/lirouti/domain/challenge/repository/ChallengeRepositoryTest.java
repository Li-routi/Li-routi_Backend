package com.lirouti.domain.challenge.repository;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.ChallengeSortType;
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

    @Test
    @DisplayName("인기순: 참여자 수 내림차순으로 정렬한다")
    void findSummaries_PopularSort_OrdersByParticipantCountDesc() {
        Challenge few = challenge("cq적은 챌린지", ChallengeCategory.HEALTH, true);
        Challenge many = challenge("cq많은 챌린지", ChallengeCategory.HEALTH, true);
        join(member(true), few, true, 1);
        join(member(true), many, true, 1);
        join(member(true), many, true, 1);
        join(member(true), many, true, 1);
        em.flush();
        em.clear();

        List<ChallengeSummaryProjection> result =
                challengeRepository.findSummaries(null, "cq", ChallengeSortType.POPULAR);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).challenge().getName()).isEqualTo("cq많은 챌린지");
        assertThat(result.get(0).participantCount()).isEqualTo(3);
        assertThat(result.get(1).participantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("참여자 수는 이탈(active=false)·탈퇴 회원을 제외한다")
    void participantCount_ExcludesInactiveParticipationAndWithdrawnMember() {
        Challenge c = challenge("cq물 마시기", ChallengeCategory.HEALTH, true);
        join(member(true), c, true, 1);   // 유효
        join(member(true), c, false, 1);  // 그만둠 → 제외
        join(member(false), c, true, 1);  // 탈퇴 회원 → 제외
        em.flush();
        em.clear();

        assertThat(challengeRepository.countActiveParticipants(c.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("카테고리 필터와 이름 검색이 동작한다")
    void findSummaries_CategoryAndKeyword() {
        challenge("cq운동아침", ChallengeCategory.EXERCISE, true);
        challenge("cq운동저녁", ChallengeCategory.EXERCISE, true);
        challenge("cq독서모임", ChallengeCategory.STUDY, true);
        em.flush();
        em.clear();

        // 카테고리 필터: EXERCISE 2건
        assertThat(challengeRepository.findSummaries(ChallengeCategory.EXERCISE, "cq", ChallengeSortType.LATEST))
                .hasSize(2);
        // 이름 검색: "운동" 포함 2건
        assertThat(challengeRepository.findSummaries(null, "운동", ChallengeSortType.LATEST))
                .hasSize(2);
        // 카테고리 + 검색 동시: STUDY이면서 "독서" 포함 1건
        assertThat(challengeRepository.findSummaries(ChallengeCategory.STUDY, "독서", ChallengeSortType.LATEST))
                .hasSize(1);
    }

    @Test
    @DisplayName("비활성 챌린지는 목록·상세에서 제외된다")
    void inactiveChallenge_Excluded() {
        Challenge inactive = challenge("cq숨긴 챌린지", ChallengeCategory.LIFE, false);
        em.flush();
        em.clear();

        assertThat(challengeRepository.findSummaries(null, "cq숨긴", ChallengeSortType.LATEST)).isEmpty();
        assertThat(challengeRepository.findByIdAndActiveTrue(inactive.getId())).isEmpty();
    }

    @Test
    @DisplayName("오늘 완료자 수: 같은 회원의 회차 다른 오늘 인증 2건을 1명으로 센다")
    void countTodayCompletions_DedupByMemberChallengeAndCurrentRound() {
        Challenge c = challenge("cq스쿼트", ChallengeCategory.EXERCISE, true);
        Member m = member(true);
        MemberChallenge mc = join(m, c, true, 2);   // 현재 회차 2
        verify(mc, 1, LocalDate.now());             // 지난 회차 인증(오늘자) — 현재 회차 아님
        verify(mc, 2, LocalDate.now());             // 현재 회차 인증

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
}
