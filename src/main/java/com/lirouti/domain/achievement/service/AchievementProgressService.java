package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.AchievementCondition;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementConditionRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementProgressCategoryRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementProgressDayRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link AchievementProgressEvent} 를 구독해 회원의 업적 진행도를 올린다.
 *
 * <p>루틴/좋아요/쿡쿡 등 행동 도메인의 트랜잭션이 커밋된 뒤에만 반응한다
 * ({@code AFTER_COMMIT}) — 행동 자체가 롤백됐는데 진행도만 올라가는 사고를 막기 위해서다.
 *
 * <p>같은 원본 사건(예: 루틴 체크 로그 1건)에 대해 이벤트가 중복 발행되는 경우를 대비해,
 * 실제 진행도 갱신에 앞서 {@link AchievementProgressEventLogService} 로 "이미 반영한
 * 사건인지"를 먼저 확정한다. 이 체크는 conditionKey 를 쓰는 업적이 여러 개라도 원본
 * 사건 1건당 딱 한 번만 수행된다 — 업적별로 나눠서 체크하면, 같은 conditionKey 를 쓰는
 * 업적이 두 개일 때 두 번째 업적은 "이미 처리됨"으로 오판해 스킵되는 버그가 생긴다.
 *
 * <p>같은 conditionKey 를 여러 업적이 공유할 수 있으므로(예: ROUTINE_COMPLETE_COUNT 를
 * CUMULATIVE_COUNT 업적과 아직 미구현인 DISTINCT_DAY_COUNT 업적이 함께 참조), {@code handle}
 * 은 업적 하나씩을 try-catch 로 격리해 처리한다 — 한 업적의 반영이 실패해도(예: 미구현
 * progressType) 형제 업적의 진행도 갱신까지 막히지 않는다.
 *
 * <p>대상 업적이 하나라도 있었는데 전부 실패한 경우, 이벤트 로그를 원복
 * ({@link AchievementProgressEventLogService#unmarkProcessed}) 해 다음 재발행 때 다시
 * 시도할 수 있게 한다. 반대로 하나라도 성공했다면 마커는 그대로 둔다 — 이미 반영된
 * 진행도를 재발행 시 중복 가산하는 것보다, 실패한 일부 형제 업적이 다음 재시도를
 * 놓치는 편이 더 안전한 트레이드오프이기 때문이다(완전한 개별 재시도가 필요하다면
 * 이벤트 로그에 업적별 처리 상태 컬럼을 추가하는 별도 작업이 필요하다).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementProgressService {

    private final AchievementRepository achievementRepository;
    private final MemberAchievementRepository memberAchievementRepository;
    private final MemberAchievementConditionRepository memberAchievementConditionRepository;
    private final MemberAchievementProgressDayRepository memberAchievementProgressDayRepository;
    private final MemberAchievementProgressCategoryRepository memberAchievementProgressCategoryRepository;
    private final MemberRepository memberRepository;
    private final AchievementProgressEventLogService achievementProgressEventLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // AFTER_COMMIT 시점엔 이미 원본 트랜잭션이 끝나 있다. 이 리스너는 MemberAchievement 등을
    // 실제로 저장해야 하므로 트랜잭션이 필요한데, RestrictedTransactionalEventListenerFactory가
    // 이 phase에서는 REQUIRES_NEW(새 트랜잭션 시작)나 NOT_SUPPORTED(트랜잭션 없음)만 허용하고
    // 기본값 REQUIRED는 거부한다 - REQUIRED는 "이미 있으면 참여, 없으면 새로 시작"인데
    // AFTER_COMMIT은 항상 트랜잭션이 없는 상태라 의미가 REQUIRES_NEW와 같아지면서도 그 사실을
    // 프레임워크가 강제로 명시하게 만든다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(AchievementProgressEvent event) {
        boolean firstTimeSeen = achievementProgressEventLogService.tryMarkProcessed(
                event.memberId(), event.conditionKey(), event.sourceType(), event.sourceId());
        if (!firstTimeSeen) {
            return; // 재시도/재발행으로 들어온 중복 이벤트 - 스킵
        }

        List<Achievement> targets = achievementRepository.findAllActiveByConditionKey(event.conditionKey());
        boolean anySucceeded = false;

        for (Achievement achievement : targets) {
            // 같은 conditionKey 를 여러 업적이 공유한다 (예: ROUTINE_COMPLETE_COUNT 를
            // CUMULATIVE_COUNT 업적과 아직 미구현인 DISTINCT_DAY_COUNT 업적이 함께 쓴다).
            // 업적 하나가 실패(예: 미구현 progressType)해도 나머지 형제 업적의 진행도
            // 갱신까지 통째로 막히면 안 되므로 업적 단위로 격리한다.
            try {
                applyProgress(achievement, event);
                anySucceeded = true;
            } catch (RuntimeException e) {
                log.error("업적 진행도 반영 실패 - achievementCode={}, progressType={}, memberId={}",
                        achievement.getCode(), achievement.getProgressType(), event.memberId(), e);
            }
        }

        if (!targets.isEmpty() && !anySucceeded) {
            // 대상 업적이 있었는데 전부 실패한 경우에만 원복한다. targets 가 비어 있는
            // 경우(매칭되는 업적이 없는 경우)는 실패가 아니므로 원복 대상이 아니다.
            achievementProgressEventLogService.unmarkProcessed(
                    event.memberId(), event.conditionKey(), event.sourceType(), event.sourceId());
        }
    }

    private void applyProgress(Achievement achievement, AchievementProgressEvent event) {
        if (!routineCategoryMatches(achievement, event)) {
            return; // 카테고리 시작 업적인데 이벤트의 루틴 카테고리가 다르다 - 무관한 이벤트
        }

        MemberAchievement memberAchievement = getOrCreate(achievement, event.memberId());

        switch (achievement.getProgressType()) {
            case NONE -> memberAchievement.achieveImmediately();
            case COMPOSITE -> applyComposite(memberAchievement, achievement, event);
            case CUMULATIVE_COUNT, DISTINCT_ROOM_COUNT ->
                    memberAchievement.increaseProgress(event.amount(), requireTargetCount(achievement));
            case DISTINCT_DAY_COUNT -> applyDistinctDayCount(memberAchievement, achievement, event);
            case WEEKLY_DISTINCT_DAY_COUNT -> applyPeriodDistinctDayCount(
                    memberAchievement, achievement, event, this::weekRangeOf);
            case MONTHLY_DISTINCT_DAY_COUNT -> applyPeriodDistinctDayCount(
                    memberAchievement, achievement, event, this::monthRangeOf);
            // ROUTINE_STREAK_DAYS 발행부(MemberRoutineStreakCommandService)가 event.amount() 에
            // "이번에 늘어난 양"이 아니라 "지금 시점의 절대 스트릭 값"을 담아 보낸다 - 그래서
            // increaseProgress(누적 가산)가 아니라 syncProgress(절대값 덮어쓰기)를 쓴다. 스트릭이
            // 끊기면 다음 완료 이벤트가 더 작은 값을 보내올 수 있는데, syncProgress는 IN_PROGRESS
            // 상태에서만 값을 바꾸므로 이미 ACHIEVED/CLAIMED 된 이후엔 끊긴 스트릭이 되돌리지 못한다.
            case STREAK_DAYS -> memberAchievement.syncProgress(event.amount(), requireTargetCount(achievement));
            case CATEGORY_COVERAGE_COUNT -> applyCategoryCoverage(memberAchievement, achievement, event);
            // GROUP_* 는 이 업적들이 condition_key 를 비워 두므로 findAllActiveByConditionKey 의
            // 결과에 애초에 포함되지 않는다 - 여기 도달한다면 조회 쿼리 또는 마이그레이션 데이터가
            // 잘못된 것이므로 조용히 넘기지 않고 바로 알아챌 수 있게 예외로 방어한다.
            case GROUP_CUMULATIVE_COUNT, GROUP_DISTINCT_DAY_COUNT ->
                    throw new IllegalStateException(
                            "업적 " + achievement.getCode() + "(" + achievement.getProgressType()
                                    + ")는 그룹 단위 파이프라인에서 처리되어야 하며 회원 단위 리스너에 "
                                    + "도달하면 안 됩니다. condition_key 설정을 확인하세요.");
        }
    }

    /**
     * DISTINCT_DAY_COUNT(평생 누적) 처리.
     * 오늘 날짜가 이 업적에 처음 기록되는 경우에만 +1 — 하루에 여러 번 이벤트가 와도
     * {@link MemberAchievementProgressDayService#tryMarkDay} 의 unique 제약 덕분에 한 번만 센다.
     */
    private void applyDistinctDayCount(MemberAchievement memberAchievement, Achievement achievement,
                                       AchievementProgressEvent event) {
        LocalDate eventDate = toKstDate(event);
        boolean isNewDay = memberAchievementProgressDayRepository
                .insertIgnore(memberAchievement.getId(), eventDate) == 1;
        if (!isNewDay) {
            return; // 오늘 치는 이미 세어짐 - 진행도 변화 없음
        }
        memberAchievement.increaseProgress(1, requireTargetCount(achievement));
    }

    /**
     * WEEKLY_DISTINCT_DAY_COUNT·MONTHLY_DISTINCT_DAY_COUNT 공통 처리.
     *
     * <p>날짜 dedup 자체는 DISTINCT_DAY_COUNT 와 같은 테이블·같은 방식을 쓴다(날짜 하나는
     * 전역적으로 한 번만 기록됨 — "이번 주"인지 "이번 달"인지는 나중에 세는 쪽에서 구분한다).
     * 새 날짜가 생겼을 때만 현재 기간 범위로 다시 세어 {@code syncProgress}(절대값 갱신)로
     * 반영한다 — 주/월이 바뀌면 지난 기간의 날짜는 범위 밖으로 밀려나 자연히 줄어든다.
     */
    private void applyPeriodDistinctDayCount(MemberAchievement memberAchievement, Achievement achievement,
                                             AchievementProgressEvent event,
                                             Function<LocalDate, LocalDate[]> rangeResolver) {
        LocalDate eventDate = toKstDate(event);
        boolean isNewDay = memberAchievementProgressDayRepository
                .insertIgnore(memberAchievement.getId(), eventDate) == 1;
        if (!isNewDay) {
            return;
        }
        LocalDate[] range = rangeResolver.apply(eventDate);
        long countInRange = memberAchievementProgressDayRepository
                .countByMemberAchievementIdAndProgressDateBetween(memberAchievement.getId(), range[0], range[1]);
        memberAchievement.syncProgress((int) countInRange, requireTargetCount(achievement));
    }

    /** 이벤트 발생일이 속한 주(월요일~일요일)의 [시작, 끝]. */
    private LocalDate[] weekRangeOf(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return new LocalDate[]{start, end};
    }

    /** 이벤트 발생일이 속한 달의 [1일, 말일]. */
    private LocalDate[] monthRangeOf(LocalDate date) {
        return new LocalDate[]{
                date.with(TemporalAdjusters.firstDayOfMonth()),
                date.with(TemporalAdjusters.lastDayOfMonth())
        };
    }

    /** 이벤트 발생 시각을 KST 기준 날짜로 변환한다. occurredAt 은 이미 KST 로 채워진다는 전제다. */
    private LocalDate toKstDate(AchievementProgressEvent event) {
        return event.occurredAt().toLocalDate();
    }

    /**
     * CATEGORY_COVERAGE_COUNT(루틴 탐험가) 처리.
     *
     * <p>{@code routineCategoryMatches} 게이트를 이미 통과했다는 건 이 이벤트의 카테고리가
     * 이 업적이 요구하는 6개 카테고리 중 하나라는 뜻이다. 그 카테고리를 처음 커버하는
     * 순간에만 커버리지 테이블에 마킹하고, 지금까지 커버한 개수로 절대값 갱신한다 —
     * "몇 번 했는지"가 아니라 "몇 개의 서로 다른 카테고리를 했는지"가 기준이라 dedup 대상이
     * 날짜가 아니라 카테고리라는 점만 {@code applyDistinctDayCount} 와 다르다.
     */
    private void applyCategoryCoverage(MemberAchievement memberAchievement, Achievement achievement,
                                       AchievementProgressEvent event) {
        Long categoryId = event.routineCategoryId();
        if (categoryId == null) {
            return; // 카테고리 정보가 없는 이벤트는 커버리지에 기여할 수 없다
        }
        boolean isNewCategory = memberAchievementProgressCategoryRepository
                .insertIgnore(memberAchievement.getId(), categoryId) == 1;
        if (!isNewCategory) {
            return; // 이미 커버한 카테고리 - 진행도 변화 없음
        }
        long coveredCount = memberAchievementProgressCategoryRepository
                .countByMemberAchievementId(memberAchievement.getId());
        memberAchievement.syncProgress((int) coveredCount, requireTargetCount(achievement));
    }

    /**
     * 카테고리 시작 업적(예: 운동 시작, 건강 시작)이나 카테고리 한정 EGG 업적(예: 배움이
     * 차곡차곡, 건강한 땀방울)이 자신과 무관한 루틴 완료 이벤트까지 반영하지 않도록 막는다.
     *
     * <p>{@code achievement.routineCategoryIds}({@code achievement_routine_category} 조인
     * 테이블) 하나로 통일돼 있다 — 예전엔 카테고리 1개짜리(운동 시작 등)만 스칼라 FK 컬럼을
     * 따로 썼는데, 그 컬럼이 {@code V20260812100950} 마이그레이션에서 제거되면서 이제
     * 카테고리 1개든 여러 개든 전부 이 조인 테이블 경로 하나만 본다.
     *
     * <p>비어 있으면 카테고리 무관 업적이라 항상 true. 값이 있으면 이벤트의
     * {@code routineCategoryId} 가 그 집합에 포함될 때만 true — 좋아요/쿡쿡처럼 카테고리
     * 개념이 없는 이벤트(routineCategoryId == null)는 이 조건을 절대 통과하지 못하므로
     * 안전하다. "전부 다 커버해야" 하는 CATEGORY_COVERAGE_COUNT(루틴 탐험가)는 이 게이트를
     * 통과한 이후 progress 반영 단계에서 insert-ignore 와 count 로 커버리지를 판정한다.
     */
    private boolean routineCategoryMatches(Achievement achievement, AchievementProgressEvent event) {
        Set<Long> requiredCategoryIds = achievement.getRoutineCategoryIds();
        if (requiredCategoryIds.isEmpty()) {
            return true; // 카테고리 무관 업적
        }
        return event.routineCategoryId() != null && requiredCategoryIds.contains(event.routineCategoryId());
    }

    /**
     * 회원이 이 업적에 처음 손을 대는 순간이면 IN_PROGRESS·progress 0 행을 만들어 둔다.
     * 화면(AchievementQueryService)은 행이 없어도 IN_PROGRESS·0 으로 보여주지만, 진행도를
     * 실제로 쌓으려면 이 시점부터는 행이 있어야 한다.
     *
     * <p>{@code findForUpdate} 로 조회하는 이유: 같은 회원의 같은 업적에 대해 이벤트가
     * 거의 동시에 두 번 들어와도(예: 짧은 시간에 두 번의 루틴 완료) 비관적 락으로 순차
     * 처리되게 하기 위해서다.
     */
    private MemberAchievement getOrCreate(Achievement achievement, Long memberId) {
        return memberAchievementRepository
                .findForUpdate(memberId, achievement.getId())
                .orElseGet(() -> memberAchievementRepository.save(
                        MemberAchievement.builder()
                                .member(memberRepository.getReferenceById(memberId))
                                .achievement(achievement)
                                .build()
                ));
    }

    private void applyComposite(MemberAchievement memberAchievement, Achievement achievement,
                                AchievementProgressEvent event) {
        AchievementCondition condition = achievement.getConditions().stream()
                .filter(c -> c.getConditionKey().equals(event.conditionKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "COMPOSITE 업적 " + achievement.getCode() + " 에 conditionKey="
                                + event.conditionKey() + " 조건 정의가 없습니다."));

        MemberAchievementCondition memberCondition = memberAchievementConditionRepository
                .findByMemberAchievementIdAndConditionKey(memberAchievement.getId(), event.conditionKey())
                .orElseGet(() -> memberAchievementConditionRepository.save(
                        MemberAchievementCondition.builder()
                                .memberAchievement(memberAchievement)
                                .conditionKey(event.conditionKey())
                                .currentValue(0)
                                .build()
                ));

        memberCondition.increase(event.amount(), condition.getTargetCount());

        if (allConditionsMet(memberAchievement, achievement)) {
            memberAchievement.achieveImmediately();
        }
    }

    /** 방금 갱신한 조건을 포함해, 이 업적의 모든 하위 조건이 목표치를 채웠는지 확인한다. */
    private boolean allConditionsMet(MemberAchievement memberAchievement, Achievement achievement) {
        Map<String, Integer> currentByKey = memberAchievementConditionRepository
                .findAllByMemberAchievementId(memberAchievement.getId()).stream()
                .collect(Collectors.toMap(
                        MemberAchievementCondition::getConditionKey,
                        MemberAchievementCondition::getCurrentValue
                ));

        return achievement.getConditions().stream()
                .allMatch(c -> currentByKey.getOrDefault(c.getConditionKey(), 0) >= c.getTargetCount());
    }

    /**
     * CUMULATIVE_COUNT·DISTINCT_ROOM_COUNT 업적은 targetCount 가 반드시 있어야 한다.
     * null 이면 데이터 정의 오류이므로(스키마상 nullable 이라 막히지 않는다) 여기서 방어한다.
     */
    private int requireTargetCount(Achievement achievement) {
        if (achievement.getTargetCount() == null) {
            throw new IllegalStateException(
                    "업적 " + achievement.getCode() + " 의 targetCount 가 비어 있습니다. progressType="
                            + achievement.getProgressType() + " 는 targetCount 가 필수입니다.");
        }
        return achievement.getTargetCount();
    }
}
