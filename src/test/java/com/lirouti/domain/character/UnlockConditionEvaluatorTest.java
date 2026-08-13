package com.lirouti.domain.character;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.character.service.evaluator.UnlockConditionEvaluator;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.MemberRoutineSchedule;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 해금 조건 판정기.
 *
 * <p><b>세 도메인을 가로지르는 것들이라 픽스처를 한 번 세우고 나눠 쓴다.</b> 개인 루틴 인증,
 * 그룹 루틴 인증, 챌린지 인증을 같은 날짜에 만들어 두고 각 판정기가 무엇을 세는지 본다.
 *
 * <p>여기서 잡고 싶은 것은 <b>조인이 어긋나 조용히 0 이 되는 것</b>이다 — 그러면 캐릭터가
 * 영영 안 열리는데 오류는 하나도 나지 않는다.
 */
@SpringBootTest
@Transactional
@DisplayName("해금 조건 판정기 테스트")
class UnlockConditionEvaluatorTest {

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 8, 10);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 8, 11);

    @Autowired
    private List<UnlockConditionEvaluator> evaluators;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private Member other;
    private Map<String, UnlockConditionEvaluator> byKey;

    @BeforeEach
    void setUp() {
        byKey = evaluators.stream()
                .collect(Collectors.toMap(UnlockConditionEvaluator::conditionKey, Function.identity()));
        me = persistMember();
        other = persistMember();
        em.flush();
    }

    private long count(String conditionKey, String param) {
        em.flush();
        em.clear();
        return byKey.get(conditionKey).count(me.getId(), param);
    }

    // ── 픽스처 ──

    private Member persistMember() {
        int n = seq.incrementAndGet();
        Member member = Member.builder()
                .email("eval" + n + "@ex.com").nickname("eval" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("eval-sid-" + n).build();
        em.persist(member);
        return member;
    }

    /** 개인 루틴 인증. 카테고리는 프리셋 id 를 그대로 쓴다(시드가 넣어 둔 행). */
    private void personalVerification(long presetCategoryId, LocalDate date, LocalTime at,
                                      LocalTime endTime) {
        RoutineCategory category = em.find(RoutineCategory.class, presetCategoryId);
        MemberRoutine routine = MemberRoutine.builder()
                .member(me).category(category).name("루틴" + seq.incrementAndGet())
                .endTime(endTime).active(true).build();
        routine.getSchedules().add(MemberRoutineSchedule.builder()
                .memberRoutine(routine).repeatDay(date.getDayOfWeek()).build());
        em.persist(routine);

        em.persist(MemberRoutineVerification.builder()
                .memberRoutine(routine).verifiedDate(date)
                .verifiedAt(LocalDateTime.of(date, at))
                .imageUrl("avatar/x.png").content("c").build());
    }

    /** 그룹 루틴 인증. 그룹·구성원·루틴·할당까지 세운다. */
    private void groupVerification(long presetCategoryId, LocalDate date, LocalTime at) {
        int n = seq.incrementAndGet();
        Group group = Group.builder().name("그룹" + n).inviteCode("INV" + n).build();
        em.persist(group);
        em.persist(GroupMember.builder()
                .member(me).group(group).role(GroupMemberRole.OWNER)
                .joinedAt(LocalDateTime.of(date, LocalTime.MIN)).build());

        GroupRoutineCategory category = em.find(GroupRoutineCategory.class, presetCategoryId);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group).category(category).title("그룹루틴" + n).description("d").build();
        em.persist(routine);

        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(me).assignedDate(date)
                .scheduledStartTime(LocalTime.of(0, 0)).scheduledEndTime(LocalTime.of(23, 59))
                .status(GroupRoutineAssignmentStatus.COMPLETED).build();
        em.persist(assignment);

        em.persist(GroupRoutineVerification.builder()
                .assignment(assignment).verifiedAt(LocalDateTime.of(date, at))
                .imageUrl("avatar/x.png").content("c").build());
    }

    /** 챌린지 인증. 좋아요를 눌러 볼 수 있도록 인증을 돌려준다. */
    private ChallengeVerification challengeVerification(Member author, ChallengeCategory category,
                                                        LocalDate date, LocalTime at) {
        Challenge challenge = Challenge.builder()
                .name("챌린지" + seq.incrementAndGet()).category(category).active(true).build();
        em.persist(challenge);

        MemberChallenge memberChallenge = MemberChallenge.builder()
                .member(author).challenge(challenge).participationRound(1)
                .currentStreak(0).joinedAt(LocalDateTime.of(date, LocalTime.MIN)).active(true).build();
        em.persist(memberChallenge);

        ChallengeVerification verification = ChallengeVerification.builder()
                .memberChallenge(memberChallenge).participationRound(1)
                .verifiedDate(date).periodStartDate(date)
                .verifiedAt(LocalDateTime.of(date, at))
                .imageUrl("avatar/x.png").content("c").build();
        em.persist(verification);
        return verification;
    }

    // ── CATEGORY_DAYS ──

    /**
     * 세 원천의 날짜를 합집합으로 모아야 한다. 원천별로 세어 더하면 같은 날이 두 번 들어간다.
     */
    @Test
    @DisplayName("카테고리 조건은 세 원천을 날짜 합집합으로 센다")
    void categoryDays_UnionsThreeSources() {
        personalVerification(1L, DAY_ONE, LocalTime.of(9, 0), LocalTime.of(23, 59));
        groupVerification(1L, DAY_ONE, LocalTime.of(10, 0));
        challengeVerification(me, ChallengeCategory.EXERCISE, DAY_TWO, LocalTime.of(11, 0));

        assertThat(count("CATEGORY_DAYS", "EXERCISE"))
                .as("같은 날 둘은 1일, 다른 날 하나가 더해져 2일")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("다른 카테고리는 세지 않는다")
    void categoryDays_IgnoresOtherCategories() {
        personalVerification(1L, DAY_ONE, LocalTime.of(9, 0), LocalTime.of(23, 59));

        assertThat(count("CATEGORY_DAYS", "MIND")).isZero();
    }

    @Test
    @DisplayName("쉼표로 나열하면 합집합이다 — 코코의 '운동 또는 건강'")
    void categoryDays_CommaMeansUnion() {
        personalVerification(1L, DAY_ONE, LocalTime.of(9, 0), LocalTime.of(23, 59));
        personalVerification(2L, DAY_TWO, LocalTime.of(9, 0), LocalTime.of(23, 59));

        assertAll(
                () -> assertThat(count("CATEGORY_DAYS", "EXERCISE,HEALTH")).isEqualTo(2),
                () -> assertThat(count("CATEGORY_DAYS", "EXERCISE")).isEqualTo(1));
    }

    // ── TIME_WINDOW_DAYS ──

    @Test
    @DisplayName("시간대 조건은 그 구간에 완료한 날만 센다")
    void timeWindowDays_CountsOnlyInsideWindow() {
        personalVerification(1L, DAY_ONE, LocalTime.of(6, 30), LocalTime.of(23, 59));
        personalVerification(1L, DAY_TWO, LocalTime.of(9, 30), LocalTime.of(23, 59));

        assertAll(
                () -> assertThat(count("TIME_WINDOW_DAYS", "05:00-07:59")).isEqualTo(1),
                () -> assertThat(count("TIME_WINDOW_DAYS", "00:00-00:59")).isZero());
    }

    @Test
    @DisplayName("시간대도 세 원천을 본다 — 그룹만 해도 센다")
    void timeWindowDays_IncludesGroup() {
        groupVerification(1L, DAY_ONE, LocalTime.of(6, 0));

        assertThat(count("TIME_WINDOW_DAYS", "05:00-07:59")).isEqualTo(1);
    }

    // ── ALL_KINDS_DAYS ──

    /** 한 종류라도 빠진 날은 세지 않는다. 까루가 "골고루 썼는가" 를 묻기 때문이다. */
    @Test
    @DisplayName("하루에 셋을 다 해야 센다")
    void allKindsDays_RequiresAllThreeSameDay() {
        personalVerification(1L, DAY_ONE, LocalTime.of(9, 0), LocalTime.of(23, 59));
        groupVerification(1L, DAY_ONE, LocalTime.of(10, 0));

        assertThat(count("ALL_KINDS_DAYS", null)).as("챌린지가 빠졌다").isZero();

        challengeVerification(me, ChallengeCategory.HEALTH, DAY_ONE, LocalTime.of(11, 0));

        assertThat(count("ALL_KINDS_DAYS", null)).isEqualTo(1);
    }

    @Test
    @DisplayName("셋을 다 했어도 날짜가 다르면 세지 않는다")
    void allKindsDays_RequiresSameDay() {
        personalVerification(1L, DAY_ONE, LocalTime.of(9, 0), LocalTime.of(23, 59));
        groupVerification(1L, DAY_TWO, LocalTime.of(10, 0));
        challengeVerification(me, ChallengeCategory.HEALTH, DAY_TWO, LocalTime.of(11, 0));

        assertThat(count("ALL_KINDS_DAYS", null)).isZero();
    }

    // ── DEADLINE_RUSH ──

    @Test
    @DisplayName("마감 1분 이내에 끝내야 센다")
    void deadlineRush_CountsOnlyNearDeadline() {
        // 마감 22:00, 완료 21:59:30 → 30초 남음
        personalVerification(1L, DAY_ONE, LocalTime.of(21, 59, 30), LocalTime.of(22, 0));
        // 마감 22:00, 완료 21:00 → 한 시간 남음
        personalVerification(1L, DAY_TWO, LocalTime.of(21, 0), LocalTime.of(22, 0));

        assertThat(count("DEADLINE_RUSH", "60")).isEqualTo(1);
    }

    /** 마감 시각과 같거나 그 뒤는 인정하지 않는다. */
    @Test
    @DisplayName("마감을 넘겨 끝낸 것은 세지 않는다")
    void deadlineRush_ExcludesAfterDeadline() {
        personalVerification(1L, DAY_ONE, LocalTime.of(22, 0), LocalTime.of(22, 0));

        assertThat(count("DEADLINE_RUSH", "60")).isZero();
    }

    // ── LIKE_GIVEN ──

    @Test
    @DisplayName("남의 인증에 누른 좋아요만 센다")
    void likeGiven_ExcludesOwnVerification() {
        ChallengeVerification mine = challengeVerification(
                me, ChallengeCategory.HEALTH, DAY_ONE, LocalTime.of(9, 0));
        ChallengeVerification others = challengeVerification(
                other, ChallengeCategory.HEALTH, DAY_ONE, LocalTime.of(9, 0));
        em.flush();

        like(mine.getId());
        like(others.getId());

        assertThat(count("LIKE_GIVEN", null)).as("자기 글에 누른 것은 빠진다").isEqualTo(1);
    }

    private void like(Long challengeVerificationId) {
        em.createNativeQuery("""
                        insert into challenge_verification_like
                            (challenge_verification_id, member_id, created_at, updated_at)
                        values (:verificationId, :memberId, NOW(6), NOW(6))
                        """)
                .setParameter("verificationId", challengeVerificationId)
                .setParameter("memberId", me.getId())
                .executeUpdate();
    }
}
