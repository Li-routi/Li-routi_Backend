package com.lirouti.domain.group.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.achievement.event.GroupAchievementProgressEvent;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;

import lombok.RequiredArgsConstructor;

/** 마감 상태 전이와 스트릭 초기화를 하나의 독립 batch 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
public class GroupRoutineAssignmentStatusRefreshBatchService {
    private static final List<GroupRoutineAssignmentStatus> UNFINISHED_STATUSES = List.of(
            GroupRoutineAssignmentStatus.PENDING,
            GroupRoutineAssignmentStatus.IN_PROGRESS
    );

    private final GroupRepository groupRepository;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRoutineVerificationLikeRepository groupRoutineVerificationLikeRepository;
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 빈 프록시를 통해 REQUIRES_NEW로 호출된다. 반환 전에 커밋되어 이 batch의 모든 잠금이 해제된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int resolveExpiredAssignments(LocalDateTime currentDateTime, int groupBatchSize) {
        LocalDate today = currentDateTime.toLocalDate();
        List<Group> groups = groupRepository.findExpiredAssignmentGroupsForUpdate(
                GroupStatus.ACTIVE,
                today,
                currentDateTime.toLocalTime(),
                UNFINISHED_STATUSES,
                PageRequest.of(0, groupBatchSize)
        );
        if (groups.isEmpty()) {
            return 0;
        }

        List<Long> groupIds = groups.stream().map(Group::getId).toList();
        List<ExpiredAssignment> assignments = groupRoutineAssignmentRepository
                .findExpiredAssignmentsByGroupIdsForUpdate(
                        groupIds,
                        today,
                        currentDateTime.toLocalTime(),
                        UNFINISHED_STATUSES
                )
                .stream()
                .map(ExpiredAssignment::from)
                .toList();
        if (assignments.isEmpty()) {
            return 0;
        }

        Map<Long, Long> activeMemberCounts = groupIds.stream().collect(Collectors.toMap(
                groupId -> groupId,
                groupId -> groupMemberRepository.countActiveMembersByGroupId(groupId, GroupMemberStatus.ACTIVE)
        ));
        List<Long> verificationIds = assignments.stream()
                .map(ExpiredAssignment::verificationId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Long> likeCounts = groupRoutineVerificationLikeRepository.countByVerificationIds(verificationIds);

        List<ExpiredAssignment> completed = assignments.stream()
                .filter(assignment -> isCompleted(assignment, activeMemberCounts.get(assignment.groupId()), likeCounts))
                .toList();
        List<ExpiredAssignment> missed = assignments.stream()
                .filter(assignment -> !completed.contains(assignment))
                .toList();
        List<Long> completedIds = completed.stream().map(ExpiredAssignment::assignmentId).toList();
        List<Long> missedIds = missed.stream().map(ExpiredAssignment::assignmentId).toList();

        int completedCount = completedIds.isEmpty() ? 0
                : groupRoutineAssignmentRepository.markAssignmentsCompletedByIds(
                        completedIds, UNFINISHED_STATUSES, GroupRoutineAssignmentStatus.COMPLETED);
        if (completedCount > 0) {
            completed.forEach(this::recordCompletedAssignmentActivity);
        }

        int missedCount = missedIds.isEmpty() ? 0 : groupRoutineAssignmentRepository.markAssignmentsMissedByIds(
                missedIds,
                UNFINISHED_STATUSES,
                GroupRoutineAssignmentStatus.MISSED
        );
        if (missedCount > 0) {
            groupMemberActivityCommandService
                    .resetCurrentStreaksForMissedAssignments(missedIds);
        }
        return completedCount + missedCount;
    }

    @Transactional
    public int markStartedAssignmentsInProgress(LocalDateTime currentDateTime) {
        return groupRoutineAssignmentRepository.markStartedAssignmentsInProgress(
                currentDateTime.toLocalDate(),
                currentDateTime.toLocalTime(),
                GroupRoutineAssignmentStatus.PENDING,
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
    }

    private boolean isCompleted(
            ExpiredAssignment assignment,
            Long activeMemberCount,
            Map<Long, Long> likeCounts
    ) {
        if (assignment.verificationId() == null || activeMemberCount == null || activeMemberCount > 6) {
            return false;
        }
        long likeCount = likeCounts.getOrDefault(assignment.verificationId(), 0L);
        long minimumLikeCount = switch (activeMemberCount.intValue()) {
            case 1, 2 -> 0;
            case 3 -> 1;
            case 4, 5 -> 2;
            case 6 -> 3;
            default -> Long.MAX_VALUE;
        };
        return likeCount >= minimumLikeCount;
    }

    /** 실제 COMPLETED 전이 뒤에만 그룹 스트릭과 완료 업적을 반영한다. */
    private void recordCompletedAssignmentActivity(ExpiredAssignment assignment) {
        groupMemberActivityCommandService.recordStreakIfAllAssignmentsCompleted(
                assignment.groupId(), assignment.memberId(), assignment.assignedDate());
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publishEvent(new GroupAchievementProgressEvent(
                assignment.groupId(), "ACH-AC-009", 1,
                "GROUP_ASSIGNMENT_COMPLETE", assignment.assignmentId()));
        if (groupMemberActivityCommandService
                .isAllMembersCompletedToday(assignment.groupId(), assignment.assignedDate())) {
            eventPublisher.publishEvent(new GroupAchievementProgressEvent(
                    assignment.groupId(), "ACH-SP-004", 1, "GROUP_ALL_COMPLETE_DAY",
                    assignment.groupId() * 10_000_000L + assignment.assignedDate().toEpochDay()));
        }
    }

    private record ExpiredAssignment(
            Long assignmentId,
            Long groupId,
            Long memberId,
            LocalDate assignedDate,
            Long verificationId
    ) {
        private static ExpiredAssignment from(GroupRoutineAssignment assignment) {
            return new ExpiredAssignment(
                    assignment.getId(),
                    assignment.getGroupRoutine().getGroup().getId(),
                    assignment.getMember().getId(),
                    assignment.getAssignedDate(),
                    assignment.getVerification() == null ? null : assignment.getVerification().getId()
            );
        }
    }
}
