package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.GroupAchievementProgress;
import com.lirouti.domain.achievement.enums.AchievementProgressType;
import com.lirouti.domain.achievement.event.GroupAchievementProgressEvent;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.repository.GroupAchievementProgressRepository;
import com.lirouti.domain.group.dto.projection.DailyAssignmentTotals;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final GroupActiveMemberSource groupActiveMemberSource;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupAchievementProgressTxHelper groupAchievementProgressTxHelper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(GroupAchievementProgressEvent event) {
        Achievement achievement = achievementRepository.findByCode(event.achievementCode())
                .orElseThrow(() -> {
                    log.error("존재하지 않는 업적 코드로 그룹 진행도 이벤트가 발행됐습니다. code={}",
                            event.achievementCode());
                    return new AchievementException(AchievementErrorCode.NOT_FOUND);
                });

        // 지원하지 않는 progressType 은 tryMarkProcessed 호출 "전"에 걸러낸다.
        // 여기서 먼저 걸러야, 이벤트가 processed 로 영구 기록된 뒤 나중에 마스터
        // 데이터를 고쳐 재발행해도 firstTimeSeen=false 라 영원히 무시되는 사고를 막는다.
        requireSupportedProgressType(achievement);

        boolean firstTimeSeen = groupAchievementProgressEventLogService.tryMarkProcessed(
                event.groupId(), achievement.getId(), event.sourceType(), event.sourceId());
        if (!firstTimeSeen) {
            return; // 재시도/재발행으로 들어온 중복 이벤트 - 스킵
        }

        GroupAchievementProgress progress = getOrCreateProgress(event.groupId(), achievement);

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

    private void requireSupportedProgressType(Achievement achievement) {
        AchievementProgressType progressType = achievement.getProgressType();
        if (progressType != AchievementProgressType.GROUP_CUMULATIVE_COUNT
                && progressType != AchievementProgressType.GROUP_DISTINCT_DAY_COUNT) {
            throw new IllegalStateException(
                    "업적 " + achievement.getCode() + "(" + progressType
                            + ")는 GroupAchievementProgressService 가 처리할 수 있는 타입이 아닙니다.");
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
     * 최초 생성 경합을 견디는 조회.
     *
     * <p>{@code findForUpdate} 는 행이 없으면 잠글 대상이 없다. 같은 그룹·업적의
     * 서로 다른 이벤트 2건이 동시에 도착하면 둘 다 빈 결과를 보고 삽입을 시도할 수
     * 있는데, 그중 하나는 {@code uk_group_achievement_progress} 유니크 제약을
     * 위반한다. 삽입은 별도 트랜잭션({@link GroupAchievementProgressTxHelper})에서
     * 수행하므로 그 실패는 현재 트랜잭션에 영향을 주지 않고, 실패 시
     * {@code findForUpdate} 로 재조회해 먼저 삽입에 성공한 행을 가져와 복구한다.
     */
    private GroupAchievementProgress getOrCreateProgress(Long groupId, Achievement achievement) {
        return groupAchievementProgressRepository.findForUpdate(groupId, achievement.getId())
                .orElseGet(() -> {
                    try {
                        return groupAchievementProgressTxHelper.createProgress(groupId, achievement);
                    } catch (DataIntegrityViolationException e) {
                        return groupAchievementProgressRepository.findForUpdate(groupId, achievement.getId())
                                .orElseThrow(() -> {
                                    log.error("동시 생성 경합 이후에도 그룹 업적 진행도를 찾을 수 없습니다. " +
                                            "groupId={}, achievementId={}", groupId, achievement.getId(), e);
                                    return e;
                                });
                    }
                });
    }

    /**
     * 목표 도달 순간의 활성 구성원 전원에게 곧바로 달성 처리한다.
     *
     * <p>회원별 처리를 {@link GroupAchievementProgressTxHelper#achieveMemberImmediately}
     * (REQUIRES_NEW)로 분리했다. 예외를 여기서 삼키기만 하면 현재 트랜잭션은 이미
     * rollback-only 로 표시되어 이후 처리와 최종 커밋까지 함께 실패하므로, 각 회원의
     * 처리를 독립된 트랜잭션으로 격리해 한 회원의 실패가 다른 회원 처리나 이 메서드가
     * 속한 트랜잭션의 커밋에 영향을 주지 않게 한다.
     */
    private void fanOutToActiveMembers(Achievement achievement, Long groupId) {
        List<Long> activeMemberIds = groupActiveMemberSource.findActiveMemberIds(groupId);
        for (Long memberId : activeMemberIds) {
            try {
                groupAchievementProgressTxHelper.achieveMemberImmediately(memberId, achievement);
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
