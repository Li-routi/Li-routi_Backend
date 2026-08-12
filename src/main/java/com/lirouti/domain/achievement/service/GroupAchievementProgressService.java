package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.GroupAchievementProgress;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.event.GroupAchievementProgressEvent;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.repository.GroupAchievementProgressRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.group.dto.projection.DailyAssignmentTotals;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link GroupAchievementProgressEvent} 를 구독해 그룹 단위 업적 진행도를 올리고,
 * 목표 도달 시 그 순간의 활성 구성원 전원에게 개별 MemberAchievement 를 fan-out 한다.
 *
 * <p>AchievementProgressService(회원 단위)와 같은 이유로 AFTER_COMMIT 에서만 반응한다 —
 * 그룹 루틴 인증 저장이 롤백됐는데 그룹 진행도만 올라가는 사고를 막기 위해서다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupAchievementProgressService {

    private final AchievementRepository achievementRepository;
    private final GroupAchievementProgressRepository groupAchievementProgressRepository;
    private final GroupAchievementProgressEventLogService groupAchievementProgressEventLogService;
    private final MemberAchievementRepository memberAchievementRepository;
    private final MemberRepository memberRepository;
    private final GroupActiveMemberSource groupActiveMemberSource;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(GroupAchievementProgressEvent event) {
        Achievement achievement = achievementRepository.findByCode(event.achievementCode())
                .orElseThrow(() -> {
                    log.error("존재하지 않는 업적 코드로 그룹 진행도 이벤트가 발행됐습니다. code={}",
                            event.achievementCode());
                    return new AchievementException(AchievementErrorCode.NOT_FOUND);
                });

        boolean firstTimeSeen = groupAchievementProgressEventLogService.tryMarkProcessed(
                event.groupId(), achievement.getId(), event.sourceType(), event.sourceId());
        if (!firstTimeSeen) {
            return; // 재시도/재발행으로 들어온 중복 이벤트 - 스킵
        }

        GroupAchievementProgress progress = groupAchievementProgressRepository
                .findForUpdate(event.groupId(), achievement.getId())
                .orElseGet(() -> groupAchievementProgressRepository.save(
                        GroupAchievementProgress.builder()
                                .groupId(event.groupId())
                                .achievement(achievement)
                                .build()
                ));

        boolean newlyAchieved = switch (achievement.getProgressType()) {
            case GROUP_CUMULATIVE_COUNT -> {
                int newValue = progress.getCurrentProgress() + event.incrementAmount();
                yield progress.syncProgressAndCheckNewlyAchieved(newValue, requireTargetCount(achievement));
            }
            case GROUP_DISTINCT_DAY_COUNT -> applyDistinctDayCount(progress, achievement, event);
            default -> throw new IllegalStateException(
                    "업적 " + achievement.getCode() + "(" + achievement.getProgressType()
                            + ")는 GroupAchievementProgressService 가 처리할 수 있는 타입이 아닙니다.");
        };

        if (newlyAchieved) {
            fanOutToActiveMembers(achievement, event.groupId());
        }
    }

    /**
     * 이벤트가 들어온 시점 기준으로 "오늘 이 그룹의 현재 가입 회차 ACTIVE 구성원 전원이
     * 완료했는지" 다시 계산해 절대값으로 반영한다. 이미 오늘 센 적이 있으면 무시된다.
     */
    private boolean applyDistinctDayCount(
            GroupAchievementProgress progress, Achievement achievement, GroupAchievementProgressEvent event
    ) {
        LocalDate today = LocalDate.now(TimeUtil.KST);
        DailyAssignmentTotals totals = groupRoutineAssignmentRepository
                .countTodayAssignmentTotalsForActiveMembers(event.groupId(), today);
        if (!totals.allCompleted()) {
            return false;
        }
        int newValue = progress.getCurrentProgress() + 1;
        return progress.syncTodayCountAndCheckNewlyAchieved(
                newValue, requireTargetCount(achievement), today);
    }

    /**
     * 목표 도달 순간의 활성 구성원 전원에게 곧바로 달성 처리한다.
     */
    private void fanOutToActiveMembers(Achievement achievement, Long groupId) {
        List<Long> activeMemberIds = groupActiveMemberSource.findActiveMemberIds(groupId);
        for (Long memberId : activeMemberIds) {
            try {
                MemberAchievement memberAchievement = memberAchievementRepository
                        .findForUpdate(memberId, achievement.getId())
                        .orElseGet(() -> memberAchievementRepository.save(
                                MemberAchievement.builder()
                                        .member(memberRepository.getReferenceById(memberId))
                                        .achievement(achievement)
                                        .build()
                        ));
                memberAchievement.achieveImmediately();
            } catch (RuntimeException e) {
                log.error("그룹 업적 fan-out 중 회원 반영 실패 - achievementCode={}, groupId={}, memberId={}",
                        achievement.getCode(), groupId, memberId, e);
            }
        }
    }

    private int requireTargetCount(Achievement achievement) {
        if (achievement.getTargetCount() == null) {
            throw new IllegalStateException(
                    "업적 " + achievement.getCode() + " 의 targetCount 가 비어 있습니다.");
        }
        return achievement.getTargetCount();
    }
}
