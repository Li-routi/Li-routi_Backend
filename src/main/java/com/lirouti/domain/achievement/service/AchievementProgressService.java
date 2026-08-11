package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.AchievementCondition;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementConditionRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
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
 */
@Service
@RequiredArgsConstructor
public class AchievementProgressService {

    private final AchievementRepository achievementRepository;
    private final MemberAchievementRepository memberAchievementRepository;
    private final MemberAchievementConditionRepository memberAchievementConditionRepository;
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
            applyProgress(achievement, event);
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
        }
    }

    /**
     * 카테고리 시작 업적(예: 운동 시작, 건강 시작)이 자신의 카테고리와 무관한 루틴
     * 완료 이벤트까지 달성 처리하지 않도록 막는다.
     *
     * <p>{@code achievement.getRoutineCategoryId()} 가 null 이면 카테고리 무관 업적이라
     * 항상 true. 그 값이 있으면 이벤트의 {@code routineCategoryId} 와 정확히 같아야
     * true — 좋아요/쿡쿡처럼 카테고리 개념이 없는 이벤트(routineCategoryId == null)는
     * 이 조건을 절대 통과하지 못하므로 안전하다.
     */
    private boolean routineCategoryMatches(Achievement achievement, AchievementProgressEvent event) {
        Long requiredCategoryId = achievement.getRoutineCategoryId();
        if (requiredCategoryId == null) {
            return true;
        }
        return requiredCategoryId.equals(event.routineCategoryId());
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
