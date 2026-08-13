package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.dto.projection.DailyAssignmentTotals;
import com.lirouti.domain.group.dto.projection.DailyScheduleAndCompletion;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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
     * @return 실제로 새로 삽입된 행 수. 이미 존재하면 0
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
            )
            select
                :groupRoutineId,
                :memberId,
                :assignedDate,
                :scheduledStartTime,
                :scheduledEndTime,
                :status,
                0,
                current_timestamp(6),
                current_timestamp(6)
            from group_routine routine
            where routine.id = :groupRoutineId
              and routine.active = true
            on duplicate key update id = group_routine_assignment.id
            """, nativeQuery = true)
    int insertIfAbsent(
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

    /** 한 루틴의 미확정 할당만 일괄 물리 삭제한다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GroupRoutineAssignment assignment
            where assignment.groupRoutine.id = :groupRoutineId
              and assignment.status in :statuses
            """)
    int deleteAllByGroupRoutineIdAndStatusIn(
            @Param("groupRoutineId") Long groupRoutineId,
            @Param("statuses") List<GroupRoutineAssignmentStatus> statuses
    );

    /**
     * 그룹 탈퇴 회원의 미완료 할당만 제거하고 완료·미이행 이력은 보존한다.
     * 인증 경로가 같은 할당 행을 잠그므로 탈퇴와 인증은 먼저 잠근 트랜잭션 순서로 처리된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from GroupRoutineAssignment assignment
            where assignment.groupRoutine.group.id = :groupId
              and assignment.member.id = :memberId
              and assignment.status in :unfinishedStatuses
              and assignment.verification is null
            """)
    int deleteUnfinishedAssignmentsForLeaver(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("unfinishedStatuses") List<GroupRoutineAssignmentStatus> unfinishedStatuses
    );

    /**
     * 그 회원의 오늘자 할당 한 건. 인증 요청이 실제로 그 사람 몫인지 확인하는 데 쓴다.
     *
     * <p>회원과 그룹을 <b>조회 조건에 넣는다.</b> 가져와서 뒤에서 비교하면 남의 할당인지
     * 없는 할당인지가 응답으로 드러난다.
     *
     * <p>그룹 루틴 인증 CommandService의 트랜잭션 안에서 비관적 쓰기 잠금을 획득한다.
     * 탈퇴의 미완료 할당 bulk delete와 같은 행을 직렬화해 먼저 확정된 요청의 결과를 보장한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from GroupRoutineAssignment assignment
            join fetch assignment.member
            join fetch assignment.groupRoutine routine
            join fetch routine.group groupEntity
            where routine.id = :groupRoutineId
              and groupEntity.id = :groupId
              and assignment.member.id = :memberId
              and assignment.assignedDate = :assignedDate
            """)
    Optional<GroupRoutineAssignment> findForVerification(
            @Param("groupRoutineId") Long groupRoutineId,
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("assignedDate") LocalDate assignedDate
    );

    /** 현재 가입 회차의 한 그룹·회원·날짜 Assignment를 잠가 스트릭 판정을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from GroupRoutineAssignment assignment
            where assignment.groupRoutine.group.id = :groupId
              and assignment.member.id = :memberId
              and assignment.assignedDate = :assignedDate
              and assignment.createdAt >= :joinedAt
            order by assignment.id
            """)
    List<GroupRoutineAssignment>
    findAllByGroupIdAndMemberIdAndAssignedDateAndCreatedAtAfterOrEqualForUpdate(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("assignedDate") LocalDate assignedDate,
            @Param("joinedAt") java.time.LocalDateTime joinedAt
    );

    /** 마감 batch가 잠근 그룹 안에서 실제 MISSED 전이 후보를 ID 순서로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from GroupRoutineAssignment assignment
            left join fetch assignment.verification
            where assignment.groupRoutine.group.id in :groupIds
              and assignment.status in :unfinishedStatuses
              and (
                    assignment.assignedDate < :today
                    or (
                        assignment.assignedDate = :today
                        and assignment.scheduledEndTime <= :currentTime
                    )
              )
            order by assignment.groupRoutine.group.id, assignment.member.id, assignment.id
            """)
    List<GroupRoutineAssignment> findExpiredAssignmentsByGroupIdsForUpdate(
            @Param("groupIds") List<Long> groupIds,
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime,
            @Param("unfinishedStatuses") List<GroupRoutineAssignmentStatus> unfinishedStatuses
    );

    /**
     * 한 루틴의 특정 날짜 할당을 ID 순서로 잠가 수정·완료·상태 전이 경합을 직렬화한다.
     * 회원 식별에는 지연 프록시의 ID를 사용하므로 불필요한 회원 엔티티 조회를 수행하지 않는다.
     *
     * @param groupRoutineId 대상 그룹 루틴 ID
     * @param assignedDate 잠글 할당 날짜
     * @return 잠긴 날짜별 할당 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select assignment
            from GroupRoutineAssignment assignment
            where assignment.groupRoutine.id = :groupRoutineId
              and assignment.assignedDate = :assignedDate
            order by assignment.id
            """)
    List<GroupRoutineAssignment> findAllByGroupRoutineIdAndAssignedDateForUpdate(
            @Param("groupRoutineId") Long groupRoutineId,
            @Param("assignedDate") LocalDate assignedDate
    );

    /**
     * 아직 확정되지 않은 할당의 수행 시간 스냅샷과 상태를 한 번에 변경한다.
     * 조회 이후 완료·마감 처리가 경합하더라도 확정 상태를 덮어쓰지 않도록 상태 조건을 유지한다.
     *
     * @param assignmentIds 변경할 할당 ID 목록
     * @param scheduledStartTime 변경할 시작 시각
     * @param scheduledEndTime 변경할 마감 시각
     * @param status 변경 후 상태
     * @param mutableStatuses 변경 가능한 기존 상태
     * @return 변경된 할당 수
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update GroupRoutineAssignment assignment
            set assignment.scheduledStartTime = :scheduledStartTime,
                assignment.scheduledEndTime = :scheduledEndTime,
                assignment.status = :status,
                assignment.version = assignment.version + 1
            where assignment.id in :assignmentIds
              and assignment.status in :mutableStatuses
            """)
    int rescheduleAssignmentsIfMutable(
            @Param("assignmentIds") List<Long> assignmentIds,
            @Param("scheduledStartTime") LocalTime scheduledStartTime,
            @Param("scheduledEndTime") LocalTime scheduledEndTime,
            @Param("status") GroupRoutineAssignmentStatus status,
            @Param("mutableStatuses") List<GroupRoutineAssignmentStatus> mutableStatuses
    );

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

    /** batch에서 잠근 후보만 한 번에 MISSED로 전이한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GroupRoutineAssignment assignment
            set assignment.status = :missedStatus,
                assignment.version = assignment.version + 1
            where assignment.id in :assignmentIds
              and assignment.status in :unfinishedStatuses
            """)
    int markAssignmentsMissedByIds(
            @Param("assignmentIds") List<Long> assignmentIds,
            @Param("unfinishedStatuses") List<GroupRoutineAssignmentStatus> unfinishedStatuses,
            @Param("missedStatus") GroupRoutineAssignmentStatus missedStatus
    );

    /** batch에서 잠근 인증 완료 후보만 COMPLETED로 전이한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GroupRoutineAssignment assignment
            set assignment.status = :completedStatus,
                assignment.version = assignment.version + 1
            where assignment.id in :assignmentIds
              and assignment.status in :unfinishedStatuses
            """)
    int markAssignmentsCompletedByIds(
            @Param("assignmentIds") List<Long> assignmentIds,
            @Param("unfinishedStatuses") List<GroupRoutineAssignmentStatus> unfinishedStatuses,
            @Param("completedStatus") GroupRoutineAssignmentStatus completedStatus
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

    /**
     * 리포트 집계용. 기간 내 회원의 그룹 루틴 할당을 날짜별로 묶어 예정 건수(총 할당 수)와
     * 완료 건수를 함께 가져온다. 그룹 루틴은 할당이 날짜마다 실제로 남아 있어, 개인 루틴처럼
     * "현재 활성 상태로 근사"할 필요 없이 그 날짜의 실제 값을 그대로 쓸 수 있다.
     * 날짜가 없는 날은 결과에 아예 나오지 않는다.
     */
    @Query("""
            select new com.lirouti.domain.group.dto.projection.DailyScheduleAndCompletion(
                a.assignedDate,
                count(a),
                sum(case when a.status = com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus.COMPLETED
                         then 1L else 0L end)
            )
            from GroupRoutineAssignment a
            where a.member.id = :memberId
              and a.assignedDate between :start and :end
            group by a.assignedDate
            """)
    List<DailyScheduleAndCompletion> findDailyScheduleAndCompletion(
            @Param("memberId") Long memberId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /** 시작·마감·마감 임박 알림 계산용 오늘의 미완료 할당을 회원·그룹과 함께 읽는다. */
    @Query("""
            select assignment from GroupRoutineAssignment assignment
            join fetch assignment.member
            join fetch assignment.groupRoutine routine
            join fetch routine.group
            where assignment.assignedDate = :date
              and assignment.status not in :terminalStatuses
              and ((:startBoundary = true and assignment.scheduledStartTime >= :from and assignment.scheduledStartTime < :to)
                or (:startBoundary = false and assignment.scheduledEndTime >= :from and assignment.scheduledEndTime < :to))
            """)
    List<GroupRoutineAssignment> findDueForNotification(
            @Param("date") LocalDate date, @Param("from") LocalTime from, @Param("to") LocalTime to,
            @Param("startBoundary") boolean startBoundary,
            @Param("terminalStatuses") List<GroupRoutineAssignmentStatus> terminalStatuses);

    /**
     * 완료 여부와 무관하게 현재 분에 종료되는 할당을 그룹 종료 알림용으로 조회한다.
     * 완료·미이행 할당은 회원 탈퇴 후에도 이력으로 남으므로, 현재 그 그룹의 ACTIVE 구성원에게만 보낸다.
     */
    @Query("""
            select assignment from GroupRoutineAssignment assignment
            join fetch assignment.member member
            join fetch assignment.groupRoutine routine
            join fetch routine.group grp
            where assignment.assignedDate = :date
              and assignment.scheduledEndTime >= :from
              and assignment.scheduledEndTime < :to
              and exists (
                  select 1 from GroupMember groupMember
                  where groupMember.group = grp and groupMember.member = member
                    and groupMember.status = com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE
              )
            """)
    List<GroupRoutineAssignment> findEndingForNotification(
            @Param("date") LocalDate date,
            @Param("from") LocalTime from,
            @Param("to") LocalTime to
    );

    /**
     * 오늘 그 그룹의 현재 가입 회차 ACTIVE 구성원 전원이 각자의 할당을 모두 완료했는지
     * 판정하기 위한 집계. "총 0건"(오늘 할당된 루틴이 없는 방)은 완료로 치지 않기 위해
     * 총 건수도 함께 반환한다.
     *
     * <p>group by 가 없는 집계이므로 대상 행이 0건이면 count=0, sum=NULL 인 행 1개를
     * 반환한다. sum(...) 을 coalesce 로 감싸지 않으면 그 NULL 이 원시형 long 인
     * {@link DailyAssignmentTotals} 생성자로 매핑되지 못해 오늘 할당이 하나도 없는
     * 그룹을 조회할 때마다 실패한다.
     */
    @Query("""
        select new com.lirouti.domain.group.dto.projection.DailyAssignmentTotals(
            count(assignment),
            coalesce(sum(case when assignment.status = com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus.COMPLETED
                     then 1L else 0L end), 0L)
        )
        from GroupRoutineAssignment assignment
        join GroupMember gm
             on gm.group.id = assignment.groupRoutine.group.id
            and gm.member.id = assignment.member.id
        where assignment.groupRoutine.group.id = :groupId
          and assignment.assignedDate = :assignedDate
          and gm.status = com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE
          and assignment.createdAt >= gm.joinedAt
        """)
    DailyAssignmentTotals countTodayAssignmentTotalsForActiveMembers(
            @Param("groupId") Long groupId, @Param("assignedDate") LocalDate assignedDate);
}
