package com.lirouti.domain.group.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;

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
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;

    /**
     * 빈 프록시를 통해 REQUIRES_NEW로 호출된다. 반환 전에 커밋되어 이 batch의 모든 잠금이 해제된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markExpiredAssignmentsMissed(LocalDateTime currentDateTime, int groupBatchSize) {
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
        List<Long> assignmentIds = groupRoutineAssignmentRepository
                .findExpiredAssignmentsByGroupIdsForUpdate(
                        groupIds,
                        today,
                        currentDateTime.toLocalTime(),
                        UNFINISHED_STATUSES
                )
                .stream()
                .map(GroupRoutineAssignment::getId)
                .toList();
        if (assignmentIds.isEmpty()) {
            return 0;
        }

        int missedCount = groupRoutineAssignmentRepository.markAssignmentsMissedByIds(
                assignmentIds,
                UNFINISHED_STATUSES,
                GroupRoutineAssignmentStatus.MISSED
        );
        if (missedCount > 0) {
            groupMemberActivityCommandService
                    .resetCurrentStreaksForMissedAssignments(assignmentIds);
        }
        return missedCount;
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
}
