package com.lirouti.domain.badge.repository;

import com.lirouti.domain.badge.entity.MemberBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberBadgeRepository extends JpaRepository<MemberBadge, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"badge"})
    List<MemberBadge> findAllByMemberId(Long memberId);

    /**
     * @return 신규 지급이면 1, 이미 보유 중이면(unique 위반, no-op) 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO member_badge (member_id, badge_id, earned_at, created_at, updated_at)
        VALUES (:memberId, :badgeId, NOW(), NOW(), NOW())
        ON DUPLICATE KEY UPDATE id = id
        """, nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("badgeId") Long badgeId);
}