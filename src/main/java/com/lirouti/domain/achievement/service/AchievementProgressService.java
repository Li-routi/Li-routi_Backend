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
 * CUMULATIVE_COUNT 업적과 DISTINCT_DAY_COUNT 업적이 함께 참조), {@code handle}
 * 은 업적 하나씩을 try-catch 로 격리해 처리한다 — 한 업적의 반영이 실패해도(예: 미구현
 * progressType) 형제 업적의 진행도 갱신까지 막히지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementProgressService {

    private final AchievementRepository achievementRepository;
    private final MemberAchievementRepository memberAchievementRepository;
    private final MemberAchievementConditionRepository memberAchievementConditionRepository;
    private final MemberAchievementProgressDayRepository memberAchievementProgressDayRepository;
    private final MemberAchievementProgressDayService memberAchievementProgressDayService;
    private final MemberAchievementProgressCategoryRepository memberAchievementProgressCategoryRepository;
    private final MemberAchievementProgressCategoryService memberAchievementProgressCategoryService;
    private final MemberRepository memberRepository;
    private final AchievementProgressEventLogService achievementProgressEventLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void handle(AchievementProgressEvent event) {
        boolean firstTimeSeen = achievementProgressEventLogService.tryMarkProcessed(
                event.memberId(), event.conditionKey(), event.sourceType(), event.sourceId());
        if (!firstTimeSeen) {
            return; // 재시도/재발행으로 들어온 중복 이벤트 - 스킵
        }

        List<Achievement> targets = achievementRepository.findAllActiveByConditionKey(event.conditionKey());
        for (Achievement achievement : targets) {
            try {
                applyProgress(achievement, event);
            } catch (RuntimeException e) {
                log.error("업적 진행도 반영 실패 - achievementCode={}, progressType={}, memberId={}",
                        achievement.getCode(), achievement.getProgressType(), event.memberId(), e);
            }
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
            // STREAK_DAYS: event.amount()에 담긴 "현재시점 연속 일수" 절대값으로 진행도를 갱신(syncProgress)한다.
            // 결석 등으로 스트릭이 초기화되었을 때 진행도도 함께 내려가야 하므로 increaseProgress(더하기) 대신 syncProgress를 사용한다.
            case STREAK_DAYS -> memberAchievement.syncProgress(event.amount(), requireTargetCount(achievement));
            case CATEGORY_COVERAGE_COUNT -> applyCategoryCoverageCount(memberAchievement, achievement, event);
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
        boolean isNewDay = memberAchievementProgressDayService.tryMarkDay(memberAchievement, eventDate);
        if (!isNewDay) {
            return; // 오늘 치는 이미 세어짐 - 진행도 변화 없음
        }
        memberAchievement.increaseProgress(1, requireTargetCount(achievement));
    }

    /**
     * WEEKLY_DISTINCT_DAY_COUNT·MONTHLY_DISTINCT_DAY_COUNT 공통 처리.
     */
    private void applyPeriodDistinctDayCount(MemberAchievement memberAchievement, Achievement achievement,
                                             AchievementProgressEvent event,
                                             Function<LocalDate, LocalDate[]> rangeResolver) {
        LocalDate eventDate = toKstDate(event);
        boolean isNewDay = memberAchievementProgressDayService.tryMarkDay(memberAchievement, eventDate);
        if (!isNewDay) {
            return;
        }
        LocalDate[] range = rangeResolver.apply(eventDate);
        long countInRange = memberAchievementProgressDayRepository
                .countByMemberAchievementIdAndProgressDateBetween(memberAchievement.getId(), range[0], range[1]);
        memberAchievement.syncProgress((int) countInRange, requireTargetCount(achievement));
    }

    /**
     * CATEGORY_COVERAGE_COUNT(루틴 탐험가) 처리.
     *
     * <p>{@link #routineCategoryMatches} 가 이미 "achievement.routineCategoryIds 안에
     * 속하는 카테고리인지"를 걸러줬으므로, 여기서는 그 카테고리를 이 업적에 대해 처음
     * 커버하는 것인지만 확정하고(mark 성공 시에만) 지금까지 커버한 서로 다른 카테고리
     * 총 개수로 다시 세어 덮어쓴다. DISTINCT_DAY_COUNT 와 동일한 이유로 syncProgress 를
     * 쓴다 - 같은 카테고리를 반복 완료해도 진행도가 중복으로 늘지 않는다.
     */
    private void applyCategoryCoverageCount(MemberAchievement memberAchievement, Achievement achievement,
                                            AchievementProgressEvent event) {
        if (event.routineCategoryId() == null) {
            return; // 카테고리 정보 없는 이벤트 - 커버리지 판정 불가
        }
        boolean isNewCategory = memberAchievementProgressCategoryService
                .tryMarkCategory(memberAchievement, event.routineCategoryId());
        if (!isNewCategory) {
            return; // 이미 커버한 카테고리 - 진행도 변화 없음
        }
        long coveredCount = memberAchievementProgressCategoryRepository
                .countByMemberAchievementId(memberAchievement.getId());
        memberAchievement.syncProgress((int) coveredCount, requireTargetCount(achievement));
    }

    /** 이벤트 발생일이 속한 주(일요일 - 월요일)의 [시작, 끝]. */
    private LocalDate[] weekRangeOf(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
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
     * 카테고리 시작 업적(예: 운동 시작, 건강 시작)이나 카테고리 한정 EGG 업적, 그리고
     * CATEGORY_COVERAGE_COUNT(루틴 탐험가)가 자신과 무관한 루틴 완료 이벤트까지 반영하지
     * 않도록 막는다.
     *
     * <p>카테고리 조건은 두 경로 중 하나로 걸린다 — 같은 업적이 둘 다 쓰지는 않는다:
     * <ul>
     *   <li>{@code achievement.routineCategoryId}(단일 FK): 카테고리 시작 업적처럼 정확히
     *   카테고리 1개만 요구할 때.</li>
     *   <li>{@code achievement.routineCategoryIds}({@code achievement_routine_category}
     *   조인 테이블): 카테고리 2개 이상 중 아무거나 해당하면 되는 경우(예: 건강한 땀방울 =
     *   운동 또는 건강), 또는 전부 다 커버해야 하는 경우(루틴 탐험가 = 6개 전부). "아무거나
     *   해당"과 "전부 다"의 구분은 이 메서드가 아니라 progressType 별 처리기
     *   ({@link #applyCategoryCoverageCount})가 담당한다 — 여기서는 "이 이벤트가 이 업적과
     *   관련은 있는 카테고리인지"만 1차로 거른다.</li>
     * </ul>
     * 둘 다 비어 있으면 카테고리 무관 업적이라 항상 true. 좋아요/쿡쿡처럼 카테고리 개념이
     * 없는 이벤트(routineCategoryId == null)는 두 경로 모두 통과하지 못하므로 안전하다.
     */
    private boolean routineCategoryMatches(Achievement achievement, AchievementProgressEvent event) {
        Long requiredCategoryId = achievement.getRoutineCategoryId();
        if (requiredCategoryId != null) {
            return requiredCategoryId.equals(event.routineCategoryId());
        }

        Set<Long> requiredCategoryIds = achievement.getRoutineCategoryIds();
        if (!requiredCategoryIds.isEmpty()) {
            return event.routineCategoryId() != null && requiredCategoryIds.contains(event.routineCategoryId());
        }

        return true; // 카테고리 무관 업적
    }

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

    private int requireTargetCount(Achievement achievement) {
        if (achievement.getTargetCount() == null) {
            throw new IllegalStateException(
                    "업적 " + achievement.getCode() + " 의 targetCount 가 비어 있습니다. progressType="
                            + achievement.getProgressType() + " 는 targetCount 가 필수입니다.");
        }
        return achievement.getTargetCount();
    }
}
