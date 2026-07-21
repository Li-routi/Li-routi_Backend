package com.lirouti.domain.challenge.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
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
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
@DisplayName("ChallengeCommandService 참여/이탈 테스트")
class ChallengeCommandServiceTest {

    @Autowired
    private ChallengeCommandService challengeCommandService;

    @Autowired
    private MemberChallengeRepository memberChallengeRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger(0);

    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("cmd-m" + n + "@ex.com").nickname("cm" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("cmd-sid-" + n).build();
        em.persist(m);
        return m;
    }

    private Challenge challenge(boolean active) {
        Challenge c = Challenge.builder()
                .name("cmd챌린지").category(ChallengeCategory.HEALTH).active(active).build();
        em.persist(c);
        return c;
    }

    private MemberChallenge join(Member m, Challenge c, boolean active, int round) {
        MemberChallenge mc = MemberChallenge.builder()
                .member(m).challenge(c)
                .participationRound(round).currentStreak(0)
                .joinedAt(LocalDateTime.now()).active(active).build();
        em.persist(mc);
        return mc;
    }

    @Test
    @DisplayName("처음 참여하면 참여 행이 생기고 회차 1, 참여중이다")
    void participate_New() {
        Member m = member();
        Challenge c = challenge(true);
        em.flush();

        ChallengeResDTO.Participation result = challengeCommandService.participate(m.getId(), c.getId());

        assertThat(result.participating()).isTrue();
        assertThat(result.participationRound()).isEqualTo(1);
        assertThat(result.challengeId()).isEqualTo(c.getId());
        assertThat(memberChallengeRepository.findByMemberIdAndChallengeId(m.getId(), c.getId()))
                .get().extracting(MemberChallenge::isParticipating).isEqualTo(true);
    }

    @Test
    @DisplayName("예전에 그만둔 챌린지에 재참여하면 회차가 1 오르고 다시 참여중이 된다")
    void participate_Rejoin() {
        Member m = member();
        Challenge c = challenge(true);
        MemberChallenge mc = join(m, c, false, 1); // 그만둔 상태
        ReflectionTestUtils.setField(mc, "currentStreak", 5);
        em.flush();

        ChallengeResDTO.Participation result = challengeCommandService.participate(m.getId(), c.getId());

        assertThat(result.participating()).isTrue();
        assertThat(result.participationRound()).isEqualTo(2); // 회차 +1
        em.flush();
        em.clear();
        MemberChallenge reloaded = memberChallengeRepository
                .findByMemberIdAndChallengeId(m.getId(), c.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStreak()).isZero();          // 재참여 시 초기화
        assertThat(reloaded.getLastVerifiedDate()).isNull();
    }

    @Test
    @DisplayName("이미 참여 중이면 예외를 던진다")
    void participate_AlreadyParticipating() {
        Member m = member();
        Challenge c = challenge(true);
        join(m, c, true, 1);
        em.flush();

        assertThatThrownBy(() -> challengeCommandService.participate(m.getId(), c.getId()))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.ALREADY_PARTICIPATING);
    }

    @Test
    @DisplayName("없거나 비활성 챌린지에는 참여할 수 없다(404)")
    void participate_InactiveChallenge() {
        Member m = member();
        Challenge c = challenge(false);
        em.flush();

        assertThatThrownBy(() -> challengeCommandService.participate(m.getId(), c.getId()))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.CHALLENGE_NOT_FOUND);
    }

    @Test
    @DisplayName("참여 중인 챌린지를 이탈하면 참여 상태만 꺼진다")
    void leave_Participating() {
        Member m = member();
        Challenge c = challenge(true);
        join(m, c, true, 1);
        em.flush();

        ChallengeResDTO.Participation result = challengeCommandService.leave(m.getId(), c.getId());

        assertThat(result.participating()).isFalse();
        assertThat(memberChallengeRepository.findByMemberIdAndChallengeId(m.getId(), c.getId()))
                .get().extracting(MemberChallenge::isParticipating).isEqualTo(false);
    }

    @Test
    @DisplayName("참여한 적 없는 챌린지를 이탈하면 예외를 던진다")
    void leave_NeverParticipated() {
        Member m = member();
        Challenge c = challenge(true);
        em.flush();

        assertThatThrownBy(() -> challengeCommandService.leave(m.getId(), c.getId()))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.NOT_PARTICIPATING);
    }

    @Test
    @DisplayName("이미 그만둔 챌린지를 다시 이탈하면 예외를 던진다")
    void leave_AlreadyLeft() {
        Member m = member();
        Challenge c = challenge(true);
        join(m, c, false, 1);
        em.flush();

        assertThatThrownBy(() -> challengeCommandService.leave(m.getId(), c.getId()))
                .isInstanceOf(ChallengeException.class)
                .hasFieldOrPropertyWithValue("code", ChallengeErrorCode.NOT_PARTICIPATING);
    }
}
