package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /**
     * 회원은 여러 그룹에 참여할 수 있으므로 회원 ID만으로 권한을 판단하지 않는다.
     * 항상 요청 대상 그룹과 회원의 참여 관계를 함께 조회한다.
     */
    Optional<GroupMember> findByGroupIdAndMemberId(Long groupId, Long memberId);

    /**
     * 대상 그룹에서 지정한 가입 상태인 구성원을 모두 조회한다.
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
            """)
    List<GroupMember> findAllByGroupIdAndStatus(
            @Param("groupId") Long groupId,
            @Param("status") GroupMemberStatus status
    );
}
