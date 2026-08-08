package com.lirouti.domain.verification.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lirouti.domain.verification.entity.GroupRoutineVerification;

public interface GroupRoutineVerificationRepository
        extends JpaRepository<GroupRoutineVerification, Long> {

    /** 그 할당에 이미 인증이 있는지. 할당 1건에 인증 1건이므로 단건이다. */
    Optional<GroupRoutineVerification> findByAssignmentId(Long assignmentId);

    /** 좋아요 경로의 groupId와 인증의 실제 소속을 한 번에 검증한다. */
    @Query("""
            select verification
            from GroupRoutineVerification verification
            join verification.assignment assignment
            join assignment.groupRoutine routine
            where verification.id = :verificationId
              and routine.group.id = :groupId
            """)
    Optional<GroupRoutineVerification> findByIdAndGroupId(
            @Param("verificationId") Long verificationId,
            @Param("groupId") Long groupId
    );

    /** 읽음 커서 대상이 현재 가입 회차에서 볼 수 있는 타인 인증인지 확인한다. */
    @Query("""
            select count(verification) > 0
            from GroupRoutineVerification verification
            join verification.assignment assignment
            join assignment.groupRoutine routine
            where verification.id = :verificationId
              and routine.group.id = :groupId
              and assignment.member.id <> :memberId
              and verification.createdAt >= :membershipStartOfDay
            """)
    boolean existsReadableByIdAndGroupIdAndViewerIdAndMembershipStartOfDay(
            @Param("verificationId") Long verificationId,
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("membershipStartOfDay") LocalDateTime membershipStartOfDay
    );

    /**
     * 그 그룹 루틴의 인증을 <b>방 멤버 전원의 것</b>으로 최신순으로 가져온다.
     *
     * <p>개인 루틴과 달리 작성자를 함께 읽는다. 방 피드는 "누가 했는가"가 핵심이라 응답에
     * 닉네임이 들어가는데, 지연 로딩인 채로 두면 페이지 크기만큼 회원 조회가 더 나간다(N+1).
     *
     * <p>{@code groupId} 를 조건에 넣는 이유는 경로의 그룹과 루틴이 실제로 짝인지 확인하기
     * 위해서다. 방 멤버 검증은 경로의 groupId 로 하므로, 루틴이 다른 방 것이면
     * <b>자기 방 자격으로 남의 방 인증을 읽게 된다.</b> 인증 API 가 같은 조건을 거는 것과 짝이다.
     *
     * <p><b>다만 요청자가 그 방 멤버인지는 여기서 보지 않는다.</b> 이 조건은 "그룹과 루틴이
     * 짝인가"만 답한다. 멤버 자격은 호출부가 먼저 확인한다(RoutineVerificationQueryService 가
     * {@code GroupValidationService} 로). 이 메서드가 걸러 준다고 믿으면 방 밖의 회원에게
     * 그대로 나간다.
     */
    @Query("""
            select verification from GroupRoutineVerification verification
            join fetch verification.assignment assignment
            join fetch assignment.member
            join assignment.groupRoutine routine
            where routine.id = :groupRoutineId
              and routine.group.id = :groupId
              and (:cursor is null or verification.id < :cursor)
            order by verification.id desc
            """)
    List<GroupRoutineVerification> findByRoutineByCursor(
            @Param("groupRoutineId") Long groupRoutineId,
            @Param("groupId") Long groupId,
            @Param("cursor") Long cursor,
            Limit limit
    );

    @Query("""
            select v from GroupRoutineVerification v
            join fetch v.assignment a
            join fetch a.groupRoutine
            where a.member.id = :memberId
              and a.assignedDate = :date
            order by v.verifiedAt desc
            """)
    List<GroupRoutineVerification> findByMemberAndDate(
            @Param("memberId") Long memberId,
            @Param("date") LocalDate date
    );
}
