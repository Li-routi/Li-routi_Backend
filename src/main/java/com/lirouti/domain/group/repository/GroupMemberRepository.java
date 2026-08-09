package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /**
     * 회원은 여러 그룹에 참여할 수 있으므로 회원 ID만으로 권한을 판단하지 않는다.
     * 항상 요청 대상 그룹과 회원의 참여 관계를 함께 조회한다.
     */
    Optional<GroupMember> findByGroupIdAndMemberId(Long groupId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select groupMember
            from GroupMember groupMember
            where groupMember.group.id = :groupId
              and groupMember.member.id = :memberId
            """)
    Optional<GroupMember> findByGroupIdAndMemberIdForUpdate(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    /** 찌르기 대상인 ACTIVE 참여 관계의 회원 ID·행 ID를 행 ID 순서로 찾는다. */
    @Query("""
            select new com.lirouti.domain.group.repository.GroupMemberLockCandidate(
                groupMember.member.id,
                groupMember.id
            )
            from GroupMember groupMember
            where groupMember.group.id = :groupId
              and groupMember.member.id in :memberIds
              and groupMember.status = com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE
            order by groupMember.id asc
            """)
    List<GroupMemberLockCandidate> findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
            @Param("groupId") Long groupId,
            @Param("memberIds") List<Long> memberIds
    );

    /**
     * 찌르기 명령이 후보 행을 GroupMember ID 순서대로 하나씩 잠근다.
     * 연관 엔티티를 join하지 않아 MySQL의 FOR UPDATE가 member·member_group까지 전파되지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select groupMember from GroupMember groupMember where groupMember.id = :groupMemberId")
    Optional<GroupMember> findByIdForUpdate(@Param("groupMemberId") Long groupMemberId);

    /**
     * 그룹 삭제가 cascade delete 전에 참여 관계를 ID 순서대로 잠근다.
     * 찌르기의 부분 잠금과 같은 순서를 사용해 교차 대기를 피한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select groupMember
            from GroupMember groupMember
            where groupMember.group.id = :groupId
            order by groupMember.id asc
            """)
    List<GroupMember> findAllByGroupIdForUpdate(@Param("groupId") Long groupId);

    /** 실제 Like INSERT와 같은 트랜잭션에서 DB의 현재 가입 회차·ACTIVE 조건을 만족할 때만 증가시킨다. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update group_member group_member
            join group_routine_assignment assignment on assignment.id = :assignmentId
            join group_routine routine on routine.id = assignment.group_routine_id
               set group_member.total_like_count = group_member.total_like_count + 1
             where group_member.id = :groupMemberId
               and group_member.status = 'ACTIVE'
               and group_member.member_id = assignment.member_id
               and group_member.group_id = routine.group_id
               and assignment.created_at >= group_member.joined_at
            """, nativeQuery = true)
    int incrementTotalLikeCountForCurrentActiveMembership(
            @Param("groupMemberId") Long groupMemberId,
            @Param("assignmentId") Long assignmentId
    );

    /** 실제 Like DELETE와 같은 트랜잭션에서 DB의 현재 가입 회차·ACTIVE 조건과 음수 방지를 함께 확인한다. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update group_member group_member
            join group_routine_assignment assignment on assignment.id = :assignmentId
            join group_routine routine on routine.id = assignment.group_routine_id
               set group_member.total_like_count = group_member.total_like_count - 1
             where group_member.id = :groupMemberId
               and group_member.status = 'ACTIVE'
               and group_member.member_id = assignment.member_id
               and group_member.group_id = routine.group_id
               and assignment.created_at >= group_member.joined_at
               and group_member.total_like_count > 0
            """, nativeQuery = true)
    int decrementTotalLikeCountForCurrentActiveMembershipIfPositive(
            @Param("groupMemberId") Long groupMemberId,
            @Param("assignmentId") Long assignmentId
    );

    /** MISSED로 실제 전이된 현재 가입 회차의 ACTIVE 참여 관계만 ID 순서로 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select groupMember
            from GroupMember groupMember
            where exists (
                select assignment
                from GroupRoutineAssignment assignment
                where assignment.id in :assignmentIds
                  and assignment.member.id = groupMember.member.id
                  and assignment.groupRoutine.group.id = groupMember.group.id
                  and assignment.createdAt >= groupMember.joinedAt
            )
              and groupMember.status = com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE
            order by groupMember.group.id, groupMember.member.id
            """)
    List<GroupMember> findAllActiveCurrentMembershipsByAssignmentIdsForUpdate(
            @Param("assignmentIds") List<Long> assignmentIds
    );

    /**
     * Preview에 표시할 ACTIVE 구성원 수만큼의 요약 항목을 ID 순서대로 만든다.
     * 캐릭터 저장 모델이 아직 없으므로 Member를 fetch join하지 않고 GroupMember ID만 조회한다.
     */
    @Query("""
            select groupMember.id
            from GroupMember groupMember
            join groupMember.member member
            where groupMember.group.id = :groupId
              and groupMember.status = :status
              and member.isActive = true
              and member.deletedAt is null
            order by groupMember.joinedAt asc, groupMember.id asc
            """)
    List<Long> findIdsByGroupIdAndStatusOrderByJoinedAtAscIdAsc(
            @Param("groupId") Long groupId,
            @Param("status") GroupMemberStatus status
    );

    /**
     * 회원이 현재 참여 중인 활성 그룹 수를 역할과 관계없이 집계한다.
     * 탈퇴·강제 퇴장 이력과 삭제된 그룹은 참여 상한에서 제외한다.
     */
    @Query("""
            select count(groupMember)
            from GroupMember groupMember
            where groupMember.member.id = :memberId
              and groupMember.status = :memberStatus
              and groupMember.group.status = :groupStatus
            """)
    long countByMemberIdAndStatusAndGroupStatus(
            @Param("memberId") Long memberId,
            @Param("memberStatus") GroupMemberStatus memberStatus,
            @Param("groupStatus") GroupStatus groupStatus
    );

    /**
     * 그룹의 ACTIVE 참여 관계 중 계정도 활성 상태인 회원 수를 역할과 관계없이 집계한다.
     * 탈퇴·강제 퇴장 관계와 탈퇴·비활성 계정은 그룹원 상한에서 제외한다.
     */
    @Query("""
            select count(groupMember)
            from GroupMember groupMember
            where groupMember.group.id = :groupId
              and groupMember.status = :status
              and groupMember.member.isActive = true
              and groupMember.member.deletedAt is null
            """)
    long countActiveMembersByGroupId(
            @Param("groupId") Long groupId,
            @Param("status") GroupMemberStatus status
    );

    /**
     * 대상 그룹에서 지정한 가입 상태이며 계정도 활성 상태인 구성원을 모두 조회한다.
     *
     * @param groupId 대상 그룹 ID
     * @param status 조회할 가입 상태
     * @return 회원까지 함께 조회한 조건에 맞는 그룹 구성원 목록
     */
    @Query("""
            select groupMember
            from GroupMember groupMember
            join fetch groupMember.member
            where groupMember.group.id = :groupId
              and groupMember.status = :status
              and groupMember.member.isActive = true
              and groupMember.member.deletedAt is null
            """)
    List<GroupMember> findAllByGroupIdAndStatus(
            @Param("groupId") Long groupId,
            @Param("status") GroupMemberStatus status
    );
}
