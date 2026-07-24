package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRoutineAssignmentRepository
        extends JpaRepository<GroupRoutineAssignment, Long>,
                GroupRoutineAssignmentRepositoryCustom {

    /**
     * 동일 루틴·회원·날짜의 할당을 멱등하게 생성한다.
     * 중복 키는 기존 행을 유지하고 그 밖의 무결성 오류는 그대로 전파한다.
     *
     * @param groupRoutineId 할당할 그룹 루틴 ID
     * @param memberId 할당 대상 회원 ID
     * @param assignedDate 할당 날짜
     * @param scheduledStartTime 할당 당시 시작 시각
     * @param scheduledEndTime 할당 당시 마감 시각
     * @param status 할당 시점의 상태 이름
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into group_routine_assignment (
                group_routine_id,
                member_id,
                assigned_date,
                scheduled_start_time,
                scheduled_end_time,
                status,
                version,
                created_at,
                updated_at
            ) values (
                :groupRoutineId,
                :memberId,
                :assignedDate,
                :scheduledStartTime,
                :scheduledEndTime,
                :status,
                0,
                current_timestamp(6),
                current_timestamp(6)
            )
            on duplicate key update id = id
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("groupRoutineId") Long groupRoutineId,
            @Param("memberId") Long memberId,
            @Param("assignedDate") LocalDate assignedDate,
            @Param("scheduledStartTime") LocalTime scheduledStartTime,
            @Param("scheduledEndTime") LocalTime scheduledEndTime,
            @Param("status") String status
    );

    /**
     * 회원에게 특정 날짜에 할당된 그룹 루틴을 조회한다.
     *
     * @param memberId 회원 ID
     * @param assignedDate 조회 날짜
     * @return 해당 날짜의 할당 목록
     */
    List<GroupRoutineAssignment> findAllByMemberIdAndAssignedDate(Long memberId, LocalDate assignedDate);

    /**
     * 인증 시각이 할당의 수행 범위에 포함될 때만 미완료 상태를 완료로 변경한다.
     *
     * @param assignmentId 완료할 할당 ID
     * @param verifiedDate 인증 날짜
     * @param verifiedTime 인증 시각
     * @param allowedStatuses 완료로 전이할 수 있는 상태
     * @param completedStatus 완료 상태
     * @return 갱신된 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GroupRoutineAssignment assignment
            set assignment.status = :completedStatus,
                assignment.version = assignment.version + 1
            where assignment.id = :assignmentId
              and assignment.status in :allowedStatuses
              and assignment.assignedDate = :verifiedDate
              and assignment.scheduledStartTime <= :verifiedTime
              and assignment.scheduledEndTime > :verifiedTime
            """)
    int markCompletedIfInProgress(
            @Param("assignmentId") Long assignmentId,
            @Param("verifiedDate") LocalDate verifiedDate,
            @Param("verifiedTime") LocalTime verifiedTime,
            @Param("allowedStatuses") List<GroupRoutineAssignmentStatus> allowedStatuses,
            @Param("completedStatus") GroupRoutineAssignmentStatus completedStatus
    );

    /**
     * 마감 시각이 지난 미완료 할당을 일괄 미이행 처리한다.
     *
     * @param today 상태 갱신 기준 날짜
     * @param currentTime 상태 갱신 기준 시각
     * @param unfinishedStatuses 미이행으로 전이할 수 있는 상태
     * @param missedStatus 미이행 상태
     * @return 갱신된 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GroupRoutineAssignment assignment
            set assignment.status = :missedStatus,
                assignment.version = assignment.version + 1
            where assignment.status in :unfinishedStatuses
              and (
                    assignment.assignedDate < :today
                    or (
                        assignment.assignedDate = :today
                        and assignment.scheduledEndTime <= :currentTime
                    )
              )
            """)
    int markExpiredAssignmentsMissed(
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime,
            @Param("unfinishedStatuses") List<GroupRoutineAssignmentStatus> unfinishedStatuses,
            @Param("missedStatus") GroupRoutineAssignmentStatus missedStatus
    );

    /**
     * 수행 시간이 시작된 오늘의 대기 할당을 진행 중 상태로 변경한다.
     *
     * @param today 상태 갱신 기준 날짜
     * @param currentTime 상태 갱신 기준 시각
     * @param pendingStatus 대기 상태
     * @param inProgressStatus 진행 중 상태
     * @return 갱신된 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GroupRoutineAssignment assignment
            set assignment.status = :inProgressStatus,
                assignment.version = assignment.version + 1
            where assignment.status = :pendingStatus
              and assignment.assignedDate = :today
              and assignment.scheduledStartTime <= :currentTime
              and assignment.scheduledEndTime > :currentTime
            """)
    int markStartedAssignmentsInProgress(
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime,
            @Param("pendingStatus") GroupRoutineAssignmentStatus pendingStatus,
            @Param("inProgressStatus") GroupRoutineAssignmentStatus inProgressStatus
    );
}
