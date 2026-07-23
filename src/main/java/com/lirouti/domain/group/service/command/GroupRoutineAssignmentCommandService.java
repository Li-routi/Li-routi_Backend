package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineScheduleRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupRoutineAssignmentCommandService {
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupRoutineScheduleRepository groupRoutineScheduleRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupValidationService groupValidationService;
    private final Clock clock;

    /** 루틴 생성일이 반복 요일이면 현재 ACTIVE 그룹원 전원에게 즉시 할당한다. */
    @Transactional
    public int assignRoutineToActiveMembersToday(GroupRoutine groupRoutine) {
        return assignRoutineToActiveMembers(groupRoutine, LocalDate.now(clock));
    }

    /** 매일 해당 요일의 모든 그룹 루틴을 ACTIVE 그룹원에게 멱등하게 할당한다. */
    @Transactional
    public int assignScheduledRoutinesForDate(LocalDate assignedDate) {
        List<GroupRoutineSchedule> schedules = groupRoutineScheduleRepository
                .findAllWithRoutineAndGroupByRepeatDay(assignedDate.getDayOfWeek());
        Map<Long, List<GroupMember>> activeMembersByGroup = new HashMap<>();

        int assignmentCount = 0;
        for (GroupRoutineSchedule schedule : schedules) {
            Long groupId = schedule.getGroupRoutine().getGroup().getId();
            List<GroupMember> activeMembers = activeMembersByGroup.computeIfAbsent(
                    groupId,
                    id -> groupMemberRepository.findAllByGroupIdAndStatus(
                            id,
                            GroupMemberStatus.ACTIVE
                    )
            );
            assignmentCount += assign(schedule, activeMembers, assignedDate);
        }
        return assignmentCount;
    }

    /** 그룹 가입 흐름에서 호출해 가입 당일의 루틴을 시간과 무관하게 즉시 할당한다. */
    @Transactional
    public int assignTodayRoutinesToMember(Long groupId, Long memberId) {
        GroupMember groupMember = groupValidationService
                .validateActiveGroupMember(groupId, memberId);
        LocalDate today = LocalDate.now(clock);
        List<GroupRoutineSchedule> schedules = groupRoutineScheduleRepository
                .findAllWithRoutineByGroupIdAndRepeatDay(groupId, today.getDayOfWeek());

        return schedules.stream()
                .mapToInt(schedule -> insertAssignment(schedule, groupMember, today))
                .sum();
    }

    /** 인증 도메인이 호출한다. 수행 시간 안의 미완료 할당만 원자적으로 COMPLETED로 변경한다. */
    @Transactional
    public void completeAssignment(Long assignmentId, LocalDateTime verifiedAt) {
        if (assignmentId == null || verifiedAt == null) {
            throw new IllegalArgumentException("할당 ID와 인증 시각은 필수입니다.");
        }

        int updated = groupRoutineAssignmentRepository.markCompletedIfInProgress(
                assignmentId,
                verifiedAt.toLocalDate(),
                verifiedAt.toLocalTime(),
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.COMPLETED
        );
        if (updated == 1) {
            return;
        }

        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new GroupException(
                        GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_NOT_FOUND
                ));
        if (assignment.getStatus() == GroupRoutineAssignmentStatus.COMPLETED) {
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_ALREADY_COMPLETED);
        }
        throw new GroupException(GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_NOT_IN_PROGRESS);
    }

    /** 시간 경계에 따라 미완료 할당을 MISSED 또는 IN_PROGRESS로 원자적으로 전이한다. */
    @Transactional
    public void refreshAssignmentStatuses(LocalDateTime currentDateTime) {
        LocalDate today = currentDateTime.toLocalDate();
        groupRoutineAssignmentRepository.markExpiredAssignmentsMissed(
                today,
                currentDateTime.toLocalTime(),
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.MISSED
        );
        groupRoutineAssignmentRepository.markStartedAssignmentsInProgress(
                today,
                currentDateTime.toLocalTime(),
                GroupRoutineAssignmentStatus.PENDING,
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
    }

    private int assignRoutineToActiveMembers(GroupRoutine groupRoutine, LocalDate assignedDate) {
        GroupRoutineSchedule schedule = groupRoutine.getSchedules().stream()
                .filter(candidate -> candidate.getRepeatDay() == assignedDate.getDayOfWeek())
                .findFirst()
                .orElse(null);
        if (schedule == null) {
            return 0;
        }

        List<GroupMember> activeMembers = groupMemberRepository.findAllByGroupIdAndStatus(
                groupRoutine.getGroup().getId(),
                GroupMemberStatus.ACTIVE
        );
        return assign(schedule, activeMembers, assignedDate);
    }

    private int assign(
            GroupRoutineSchedule schedule,
            List<GroupMember> groupMembers,
            LocalDate assignedDate
    ) {
        return groupMembers.stream()
                .mapToInt(groupMember -> insertAssignment(schedule, groupMember, assignedDate))
                .sum();
    }

    private int insertAssignment(
            GroupRoutineSchedule schedule,
            GroupMember groupMember,
            LocalDate assignedDate
    ) {
        groupRoutineAssignmentRepository.insertIfAbsent(
                schedule.getGroupRoutine().getId(),
                groupMember.getMember().getId(),
                assignedDate,
                schedule.getStartTime(),
                schedule.getEndTime(),
                initialStatus(assignedDate, schedule).name()
        );
        return 1;
    }

    private GroupRoutineAssignmentStatus initialStatus(
            LocalDate assignedDate,
            GroupRoutineSchedule schedule
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime scheduledStart = assignedDate.atTime(schedule.getStartTime());
        LocalDateTime scheduledEnd = assignedDate.atTime(schedule.getEndTime());
        if (now.isBefore(scheduledStart)) {
            return GroupRoutineAssignmentStatus.PENDING;
        }
        if (now.isBefore(scheduledEnd)) {
            return GroupRoutineAssignmentStatus.IN_PROGRESS;
        }
        return GroupRoutineAssignmentStatus.MISSED;
    }
}
