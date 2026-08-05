package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.verification.dto.projection.DailyAssignmentStat;
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

    /**
     * 그 회원의 오늘자 할당 한 건. 인증 요청이 실제로 그 사람 몫인지 확인하는 데 쓴다.
     *
     * <p>회원과 그룹을 <b>조회 조건에 넣는다.</b> 가져와서 뒤에서 비교하면 남의 할당인지
     * 없는 할당인지가 응답으로 드러난다.
     *
     * <p>루틴과 그룹을 <b>함께 가져온다.</b> 둘 다 지연 로딩이라, 트랜잭션 밖에서 이 결과의
     * 연관을 건드리면 LazyInitializationException 이 난다. 호출부(인증)는 트랜잭션 경계를
     * 갖지 않으므로 여기서 채워 보낸다.
     */
    @Query("""
            select assignment
            from GroupRoutineAssignment assignment
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
     * 리포트 집계용. 기간 내 회원의 그룹 루틴 할당을 날짜별로 묶어 총 할당 수(분모)와
     * 완료 수(분자)를 함께 가져온다. 날짜가 없는 날은 결과에 아예 나오지 않는다.
     */
    @Query("""
            select new com.lirouti.domain.group.dto.projection.DailyAssignmentStat(
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
    List<DailyAssignmentStat> findDailyAssignmentStats(
            @Param("memberId") Long memberId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
