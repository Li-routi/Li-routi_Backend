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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupRoutineAssignmentCommandService {
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupRoutineScheduleRepository groupRoutineScheduleRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupValidationService groupValidationService;
    private final Clock clock;

    /**
     * 루틴 생성일이 반복 요일이면 현재 ACTIVE 그룹원 전원에게 즉시 할당한다.
     *
     * @param groupRoutine 새로 생성된 그룹 루틴
     * @return 당일 할당 대상 수
     */
    @Transactional
    public int assignRoutineToActiveMembersToday(GroupRoutine groupRoutine) {
        LocalDate today = LocalDate.now(clock);
        int assignmentCount = assignRoutineToActiveMembers(groupRoutine, today);
        log.debug("그룹 루틴 생성일 할당 처리를 완료했습니다. "
                        + "routineId={}, assignedDate={}, assignmentCount={}",
                groupRoutine.getId(), today, assignmentCount);
        return assignmentCount;
    }

    /**
     * 지정한 날짜의 반복 요일에 해당하는 그룹 루틴을 ACTIVE 그룹원에게 멱등하게 할당한다.
     *
     * @param assignedDate 할당을 생성할 날짜
     * @return 할당 대상 수
     */
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
        if (assignmentCount > 0) {
            log.info("일일 그룹 루틴 할당 처리를 완료했습니다. "
                            + "assignedDate={}, scheduleCount={}, groupCount={}, assignmentCount={}",
                    assignedDate, schedules.size(), activeMembersByGroup.size(), assignmentCount);
        } else {
            log.debug("생성할 일일 그룹 루틴 할당이 없습니다. assignedDate={}", assignedDate);
        }
        return assignmentCount;
    }

    /**
     * 그룹 가입 흐름에서 호출해 가입 당일의 반복 루틴을 회원에게 즉시 할당한다.
     *
     * @param groupId 가입한 그룹 ID
     * @param memberId 가입한 회원 ID
     * @return 가입 당일 할당 대상 수
     */
    @Transactional
    public int assignTodayRoutinesToMember(Long groupId, Long memberId) {
        GroupMember groupMember = groupValidationService
                .validateActiveGroupMember(groupId, memberId);
        LocalDate today = LocalDate.now(clock);
        List<GroupRoutineSchedule> schedules = groupRoutineScheduleRepository
                .findAllWithRoutineByGroupIdAndRepeatDay(groupId, today.getDayOfWeek());

        int assignmentCount = schedules.stream()
                .mapToInt(schedule -> insertAssignment(schedule, groupMember, today))
                .sum();
        log.debug("그룹 가입 회원의 당일 루틴 할당 처리를 완료했습니다. "
                        + "groupId={}, memberId={}, assignedDate={}, assignmentCount={}",
                groupId, memberId, today, assignmentCount);
        return assignmentCount;
    }

    /**
     * 수행 시간 안의 미완료 할당만 원자적으로 완료 처리한다.
     * 마감 시각은 인증 가능 범위에서 제외한다.
     *
     * @param assignmentId 완료할 할당 ID
     * @param verifiedAt 서버가 기록한 인증 시각
     * @throws GroupException 할당이 없거나 완료할 수 없는 상태인 경우
     */
    @Transactional
    public void completeAssignment(Long assignmentId, LocalDateTime verifiedAt) {
        if (assignmentId == null || verifiedAt == null) {
            log.warn("그룹 루틴 할당 완료 요청에 필수값이 없습니다. "
                            + "assignmentId={}, verifiedAtPresent={}",
                    assignmentId, verifiedAt != null);
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
            log.info("그룹 루틴 할당 완료 처리를 완료했습니다. assignmentId={}, verifiedAt={}",
                    assignmentId, verifiedAt);
            return;
        }

        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> {
                    log.warn("완료할 그룹 루틴 할당을 찾을 수 없습니다. assignmentId={}", assignmentId);
                    return new GroupException(GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_NOT_FOUND);
                });
        if (assignment.getStatus() == GroupRoutineAssignmentStatus.COMPLETED) {
            log.warn("이미 완료된 그룹 루틴 할당의 재완료 요청을 차단했습니다. assignmentId={}",
                    assignmentId);
            throw new GroupException(GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_ALREADY_COMPLETED);
        }
        log.warn("수행 가능 시간이 아닌 그룹 루틴 할당의 완료 요청을 차단했습니다. "
                        + "assignmentId={}, status={}",
                assignmentId, assignment.getStatus());
        throw new GroupException(GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_NOT_IN_PROGRESS);
    }

    /**
     * 기준 시각에 마감된 할당을 먼저 미이행 처리한 뒤 시작된 할당을 진행 중으로 전이한다.
     *
     * @param currentDateTime 상태 전이 기준 시각
     */
    @Transactional
    public void refreshAssignmentStatuses(LocalDateTime currentDateTime) {
        LocalDate today = currentDateTime.toLocalDate();
        int missedCount = groupRoutineAssignmentRepository.markExpiredAssignmentsMissed(
                today,
                currentDateTime.toLocalTime(),
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.MISSED
        );
        int inProgressCount = groupRoutineAssignmentRepository.markStartedAssignmentsInProgress(
                today,
                currentDateTime.toLocalTime(),
                GroupRoutineAssignmentStatus.PENDING,
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
        if (missedCount > 0 || inProgressCount > 0) {
            log.info("그룹 루틴 할당 상태 갱신을 완료했습니다. "
                            + "currentDateTime={}, missedCount={}, inProgressCount={}",
                    currentDateTime, missedCount, inProgressCount);
        }
    }

    /**
     * 루틴의 반복 요일이 할당 날짜와 일치하면 ACTIVE 그룹원 전체에게 할당한다.
     *
     * @param groupRoutine 할당할 그룹 루틴
     * @param assignedDate 할당 날짜
     * @return 할당 대상 수, 반복 요일이 아니면 0
     */
    private int assignRoutineToActiveMembers(GroupRoutine groupRoutine, LocalDate assignedDate) {
        GroupRoutineSchedule schedule = groupRoutine.getSchedules().stream()
                .filter(candidate -> candidate.getRepeatDay() == assignedDate.getDayOfWeek())
                .findFirst()
                .orElse(null);
        if (schedule == null) {
            log.debug("생성일에 해당하는 그룹 루틴 일정이 없어 할당을 생략합니다. "
                            + "routineId={}, assignedDate={}",
                    groupRoutine.getId(), assignedDate);
            return 0;
        }

        List<GroupMember> activeMembers = groupMemberRepository.findAllByGroupIdAndStatus(
                groupRoutine.getGroup().getId(),
                GroupMemberStatus.ACTIVE
        );
        return assign(schedule, activeMembers, assignedDate);
    }

    /**
     * 하나의 일정과 날짜를 구성원 목록에 적용한다.
     *
     * @param schedule 할당할 요일별 일정
     * @param groupMembers 할당 대상 구성원
     * @param assignedDate 할당 날짜
     * @return 할당 대상 수
     */
    private int assign(
            GroupRoutineSchedule schedule,
            List<GroupMember> groupMembers,
            LocalDate assignedDate
    ) {
        return groupMembers.stream()
                .mapToInt(groupMember -> insertAssignment(schedule, groupMember, assignedDate))
                .sum();
    }

    /**
     * 일정의 시간 범위를 스냅샷으로 저장해 날짜별 할당을 멱등하게 생성한다.
     *
     * @param schedule 할당할 일정
     * @param groupMember 할당 대상 그룹 구성원
     * @param assignedDate 할당 날짜
     * @return 할당 대상 한 명을 나타내는 1
     */
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

    /**
     * 현재 시각과 할당 시간 범위를 비교해 최초 상태를 결정한다.
     *
     * @param assignedDate 할당 날짜
     * @param schedule 할당할 일정
     * @return 시작 전이면 대기, 수행 시간이면 진행 중, 마감 후면 미이행 상태
     */
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
