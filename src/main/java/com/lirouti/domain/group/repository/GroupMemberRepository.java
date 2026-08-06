package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
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

    /**
     * Preview에 표시할 ACTIVE 구성원 수만큼의 요약 항목을 ID 순서대로 만든다.
     * 캐릭터 저장 모델이 아직 없으므로 Member를 fetch join하지 않고 GroupMember ID만 조회한다.
     */
    @Query("""
            select groupMember.id
            from GroupMember groupMember
            where groupMember.group.id = :groupId
              and groupMember.status = :status
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
