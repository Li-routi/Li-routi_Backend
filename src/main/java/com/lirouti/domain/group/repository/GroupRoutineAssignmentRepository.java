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
        extends JpaRepository<GroupRoutineAssignment, Long> {

    /** 동일 날짜 할당을 멱등하게 생성한다. 중복 키 외의 무결성 오류는 그대로 전파한다. */
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

    List<GroupRoutineAssignment> findAllByMemberIdAndAssignedDate(Long memberId, LocalDate assignedDate);

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
