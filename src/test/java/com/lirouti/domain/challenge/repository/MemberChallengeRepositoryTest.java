package com.lirouti.domain.challenge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
@DisplayName("MemberChallengeRepository 내 참여 목록 테스트")
class MemberChallengeRepositoryTest {

    @Autowired
    private MemberChallengeRepository memberChallengeRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("mc-m" + n + "@ex.com").nickname("mcm" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mc-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge(String name, ChallengeCategory category, boolean active) {
        Challenge c = Challenge.builder().name(name).category(category).active(active).build();
        em.persist(c);
        return c;
    }

    private void join(Member m, Challenge c, boolean active, LocalDateTime joinedAt) {
        em.persist(MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(1).currentStreak(0)
                .joinedAt(joinedAt).active(active).build());
    }

    @Test
    @DisplayName("내 활성 참여만, 최근 참여 순으로 돌려준다")
    void findMyActiveChallenges_OnlyMineActive_NewestFirst() {
        Member me = member();
        Member other = member();
        Challenge older = challenge("mc먼저", ChallengeCategory.HEALTH, true);
        Challenge newer = challenge("mc나중", ChallengeCategory.HEALTH, true);
        Challenge left = challenge("mc그만둠", ChallengeCategory.HEALTH, true);
        Challenge others = challenge("mc남의것", ChallengeCategory.HEALTH, true);

        join(me, older, true, LocalDateTime.of(2026, 7, 1, 9, 0));
        join(me, newer, true, LocalDateTime.of(2026, 7, 10, 9, 0));
        join(me, left, false, LocalDateTime.of(2026, 7, 5, 9, 0)); // 이탈 → 제외
        join(other, others, true, LocalDateTime.of(2026, 7, 9, 9, 0)); // 남의 참여 → 제외
        em.flush();
        em.clear();

        List<Challenge> result = memberChallengeRepository.findMyActiveChallenges(me.getId(), null, null);

        assertThat(result).extracting(Challenge::getName).containsExactly("mc나중", "mc먼저");
    }

    @Test
    @DisplayName("비활성 챌린지에 참여 중이어도 목록에서 제외된다")
    void findMyActiveChallenges_ExcludesInactiveChallenge() {
        Member me = member();
        Challenge hidden = challenge("mc숨김", ChallengeCategory.LIFE, false);
        join(me, hidden, true, LocalDateTime.now());
        em.flush();
        em.clear();

        assertThat(memberChallengeRepository.findMyActiveChallenges(me.getId(), null, null)).isEmpty();
    }

    @Test
    @DisplayName("카테고리·이름 검색 필터가 동작한다")
    void findMyActiveChallenges_CategoryAndKeyword() {
        Member me = member();
        join(me, challenge("mc운동아침", ChallengeCategory.EXERCISE, true), true, LocalDateTime.now());
        join(me, challenge("mc운동저녁", ChallengeCategory.EXERCISE, true), true, LocalDateTime.now());
        join(me, challenge("mc독서", ChallengeCategory.STUDY, true), true, LocalDateTime.now());
        em.flush();
        em.clear();

        assertThat(memberChallengeRepository.findMyActiveChallenges(me.getId(), ChallengeCategory.EXERCISE, null))
                .hasSize(2);
        assertThat(memberChallengeRepository.findMyActiveChallenges(me.getId(), null, "독서"))
                .hasSize(1);
    }

    @Test
    @DisplayName("findByMemberIdAndChallengeId는 이탈한 행도 찾는다(재참여·중복 판단용)")
    void findByMemberIdAndChallengeId_FindsEvenAfterLeave() {
        Member me = member();
        Challenge c = challenge("mc재참여", ChallengeCategory.HOBBY, true);
        join(me, c, false, LocalDateTime.now());
        em.flush();
        em.clear();

        assertThat(memberChallengeRepository.findByMemberIdAndChallengeId(me.getId(), c.getId()))
                .isPresent()
                .get().extracting(MemberChallenge::isParticipating).isEqualTo(false);
    }
}
