package com.lirouti.domain.group.service.command;

import com.lirouti.domain.achievement.event.GroupAchievementProgressEvent;
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
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.GroupRoutineScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupRoutineAssignmentCommandService {
    private static final int EXPIRED_ASSIGNMENT_GROUP_BATCH_SIZE = 100;
    private static final Set<GroupRoutineAssignmentStatus> TERMINAL_STATUSES = EnumSet.of(
            GroupRoutineAssignmentStatus.COMPLETED,
            GroupRoutineAssignmentStatus.MISSED
    );
    private static final List<GroupRoutineAssignmentStatus> MUTABLE_STATUSES = List.of(
            GroupRoutineAssignmentStatus.PENDING,
            GroupRoutineAssignmentStatus.IN_PROGRESS
    );

    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupRoutineRepository groupRoutineRepository;
    private final GroupRoutineScheduleRepository groupRoutineScheduleRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;
    private final GroupRoutineAssignmentStatusRefreshBatchService statusRefreshBatchService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 루틴 삭제 시 완료되지 않은 모든 회원의 할당을 한 번에 물리 삭제한다. */
    @Transactional
    public int deleteMutableAssignments(Long groupRoutineId) {
        if (groupRoutineId == null) {
            throw new IllegalArgumentException("그룹 루틴 ID는 필수입니다.");
        }
        int deletedCount = groupRoutineAssignmentRepository
                .deleteAllByGroupRoutineIdAndStatusIn(groupRoutineId, MUTABLE_STATUSES);
        log.debug("그룹 루틴의 미확정 할당을 삭제했습니다. routineId={}, deletedCount={}",
                groupRoutineId, deletedCount);
        return deletedCount;
    }

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
     * 수정된 반복 일정에 맞춰 오늘의 미확정 할당을 ACTIVE 구성원 기준으로 동기화한다.
     * 완료·미이행 상태는 확정 이력으로 간주해 시간 스냅샷과 상태를 그대로 보존한다.
     *
     * @param groupRoutine 수정된 그룹 루틴
     * @return 동기화 후 ACTIVE 구성원이 오늘 조회할 수 있는 해당 루틴 할당 수
     */
    @Transactional
    public int synchronizeRoutineAssignmentsToday(GroupRoutine groupRoutine) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        GroupRoutineSchedule todaySchedule = findSchedule(groupRoutine, today);

        List<GroupRoutineAssignment> existingAssignments = groupRoutineAssignmentRepository
                .findAllByGroupRoutineIdAndAssignedDateForUpdate(groupRoutine.getId(), today);
        Map<Long, GroupMember> activeMembersByMemberId = groupMemberRepository
                .findAllByGroupIdAndStatus(
                        groupRoutine.getGroup().getId(),
                        GroupMemberStatus.ACTIVE
                ).stream()
                .collect(Collectors.toMap(
                        groupMember -> groupMember.getMember().getId(),
                        Function.identity()
                ));

        List<GroupRoutineAssignment> assignmentsToDelete = existingAssignments.stream()
                .filter(assignment -> !isTerminal(assignment))
                .filter(assignment -> todaySchedule == null
                        || !activeMembersByMemberId.containsKey(assignment.getMember().getId()))
                .toList();
        if (!assignmentsToDelete.isEmpty()) {
            groupRoutineAssignmentRepository.deleteAll(assignmentsToDelete);
            groupRoutineAssignmentRepository.flush();
        }

        if (todaySchedule == null) {
            int preservedCount = (int) existingAssignments.stream()
                    .filter(this::isTerminal)
                    .filter(assignment -> activeMembersByMemberId
                            .containsKey(assignment.getMember().getId()))
                    .count();
            log.debug("수정된 반복 일정에 오늘 요일이 없어 미확정 할당을 제거했습니다. "
                            + "routineId={}, assignedDate={}, preservedCount={}",
                    groupRoutine.getId(), today, preservedCount);
            return preservedCount;
        }

        List<Long> mutableAssignmentIds = existingAssignments.stream()
                .filter(assignment -> !isTerminal(assignment))
                .filter(assignment -> activeMembersByMemberId
                        .containsKey(assignment.getMember().getId()))
                .map(GroupRoutineAssignment::getId)
                .toList();
        if (!mutableAssignmentIds.isEmpty()) {
            groupRoutineAssignmentRepository.rescheduleAssignmentsIfMutable(
                    mutableAssignmentIds,
                    todaySchedule.getStartTime(),
                    todaySchedule.getEndTime(),
                    initialStatus(today, todaySchedule, now),
                    MUTABLE_STATUSES
            );
        }

        Set<Long> assignedMemberIds = existingAssignments.stream()
                .map(assignment -> assignment.getMember().getId())
                .collect(Collectors.toSet());
        activeMembersByMemberId.forEach((memberId, groupMember) -> {
            if (!assignedMemberIds.contains(memberId)) {
                insertAssignment(todaySchedule, groupMember, today, now);
            }
        });

        log.debug("수정된 반복 일정의 오늘 할당 동기화를 완료했습니다. "
                        + "routineId={}, assignedDate={}, assignmentCount={}",
                groupRoutine.getId(), today, activeMembersByMemberId.size());
        return activeMembersByMemberId.size();
    }

    /** 그룹 탈퇴 회원의 미완료 할당을 제거하고 확정된 수행 이력은 보존한다. */
    @Transactional
    public int deleteUnfinishedAssignmentsForLeaver(Long groupId, Long memberId) {
        int deletedCount = groupRoutineAssignmentRepository.deleteUnfinishedAssignmentsForLeaver(
                groupId,
                memberId,
                MUTABLE_STATUSES
        );
        log.debug("그룹 탈퇴 회원의 미완료 할당을 제거했습니다. groupId={}, memberId={}, deletedCount={}",
                groupId, memberId, deletedCount);
        return deletedCount;
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
            if (!lockActiveRoutine(schedule)) {
                continue;
            }
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
     * 가입 상태 검증은 호출한 가입 Command가 담당한다. 이 서비스는 전달받은 가입 기준 시각을
     * 기준으로 루틴 조회·상태 판정·멱등 생성만 수행한다.
     *
     * @param groupId 잠금 및 가입 검증을 마친 그룹 ID
     * @param memberId 잠금 및 가입 검증을 마친 회원 ID
     * @param joinedAt 가입 Command가 한 번만 확정한 가입 기준 시각
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignTodayRoutinesToMember(
            Long groupId,
            Long memberId,
            LocalDateTime joinedAt
    ) {
        if (groupId == null || memberId == null || joinedAt == null) {
            throw new IllegalArgumentException("그룹 ID, 회원 ID와 가입 기준 시각은 필수입니다.");
        }
        LocalDate today = joinedAt.toLocalDate();
        List<GroupRoutineSchedule> schedules = groupRoutineScheduleRepository
                .findAllWithRoutineByGroupIdAndRepeatDay(groupId, today.getDayOfWeek());

        int assignmentCount = schedules.stream()
                .filter(schedule -> schedule.getEndTime().isAfter(joinedAt.toLocalTime()))
                .filter(this::lockActiveRoutine)
                .mapToInt(schedule -> insertAssignment(schedule, memberId, today, joinedAt))
                .sum();
        log.debug("그룹 가입 회원의 당일 루틴 할당 처리를 완료했습니다. "
                        + "groupId={}, memberId={}, assignedDate={}, assignmentCount={}",
                groupId, memberId, today, assignmentCount);
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

    /** 실제 완료 전이와 현재 가입 회차 스트릭 갱신을 같은 트랜잭션으로 묶는다. */
    @Transactional
    public void completeAssignmentAndRecordActivity(
            GroupRoutineAssignment assignment,
            LocalDateTime verifiedAt
    ) {
        if (assignment == null) {
            throw new IllegalArgumentException("그룹 루틴 할당은 필수입니다.");
        }
        completeAssignment(assignment.getId(), verifiedAt);

        Long groupId = assignment.getGroupRoutine().getGroup().getId();
        Long memberId = assignment.getMember().getId();
        LocalDate assignedDate = assignment.getAssignedDate();

        groupMemberActivityCommandService.recordStreakIfAllAssignmentsCompleted(
                groupId, memberId, assignedDate);

        // AC-009: 방 구성원 전체 인증 합계. sourceId로 assignment.getId()를 써서
        // 재발행돼도 group_achievement_progress_event_log unique 제약이 중복 반영을 막는다.
        eventPublisher.publishEvent(new GroupAchievementProgressEvent(
                groupId, "ACH-AC-009", 1, "GROUP_ASSIGNMENT_COMPLETE", assignment.getId()));

        // SP-004: 같은 날 구성원 전원 인증. "전원 완료" 판정 자체는
        // recordStreakIfAllAssignmentsCompleted와 동일 조건이라 그 결과를 재사용해야 한다.
        if (groupMemberActivityCommandService.isAllMembersCompletedToday(groupId, assignedDate)) {
            eventPublisher.publishEvent(new GroupAchievementProgressEvent(
                    groupId, "ACH-SP-004", 1, "GROUP_ALL_COMPLETE_DAY",
                    groupId * 10_000_000L + assignedDate.toEpochDay())); // 그룹+날짜 합성 sourceId
        }
    }

    /**
     * 기준 시각에 마감된 할당을 먼저 미이행 처리한 뒤 시작된 할당을 진행 중으로 전이한다.
     *
     * @param currentDateTime 상태 전이 기준 시각
     */
    public void refreshAssignmentStatuses(LocalDateTime currentDateTime) {
        int missedCount = 0;
        while (true) {
            int batchMissedCount = statusRefreshBatchService
                    .markExpiredAssignmentsMissed(
                            currentDateTime,
                            EXPIRED_ASSIGNMENT_GROUP_BATCH_SIZE
                    );
            if (batchMissedCount == 0) {
                break;
            }
            missedCount += batchMissedCount;
        }
        int inProgressCount = statusRefreshBatchService
                .markStartedAssignmentsInProgress(currentDateTime);
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
        GroupRoutineSchedule schedule = findSchedule(groupRoutine, assignedDate);
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
     * @return 실제로 새로 삽입된 행 수. 이미 할당돼 있으면 0
     */
    private int insertAssignment(
            GroupRoutineSchedule schedule,
            GroupMember groupMember,
            LocalDate assignedDate
    ) {
        return insertAssignment(
                schedule,
                groupMember,
                assignedDate,
                LocalDateTime.now(clock)
        );
    }

    /**
     * 삭제와 할당 생성을 같은 루틴 행 잠금으로 직렬화한다.
     * 삭제가 먼저 끝난 경우 비활성 루틴은 할당 대상에서 제외한다.
     */
    private boolean lockActiveRoutine(GroupRoutineSchedule schedule) {
        Long routineId = schedule.getGroupRoutine().getId();
        return groupRoutineRepository.findActiveByIdForUpdate(routineId).isPresent();
    }

    private int insertAssignment(
            GroupRoutineSchedule schedule,
            GroupMember groupMember,
            LocalDate assignedDate,
            LocalDateTime referenceTime
    ) {
        return insertAssignment(schedule, groupMember.getMember().getId(), assignedDate, referenceTime);
    }

    private int insertAssignment(
            GroupRoutineSchedule schedule,
            Long memberId,
            LocalDate assignedDate,
            LocalDateTime referenceTime
    ) {
        return groupRoutineAssignmentRepository.insertIfAbsent(
                schedule.getGroupRoutine().getId(),
                memberId,
                assignedDate,
                schedule.getStartTime(),
                schedule.getEndTime(),
                initialStatus(assignedDate, schedule, referenceTime).name()
        );
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
            GroupRoutineSchedule schedule,
            LocalDateTime referenceTime
    ) {
        LocalDateTime scheduledStart = assignedDate.atTime(schedule.getStartTime());
        LocalDateTime scheduledEnd = assignedDate.atTime(schedule.getEndTime());
        if (referenceTime.isBefore(scheduledStart)) {
            return GroupRoutineAssignmentStatus.PENDING;
        }
        if (referenceTime.isBefore(scheduledEnd)) {
            return GroupRoutineAssignmentStatus.IN_PROGRESS;
        }
        return GroupRoutineAssignmentStatus.MISSED;
    }

    private GroupRoutineSchedule findSchedule(
            GroupRoutine groupRoutine,
            LocalDate assignedDate
    ) {
        return groupRoutine.getSchedules().stream()
                .filter(candidate -> candidate.getRepeatDay() == assignedDate.getDayOfWeek())
                .findFirst()
                .orElse(null);
    }

    private boolean isTerminal(GroupRoutineAssignment assignment) {
        return TERMINAL_STATUSES.contains(assignment.getStatus());
    }
}
