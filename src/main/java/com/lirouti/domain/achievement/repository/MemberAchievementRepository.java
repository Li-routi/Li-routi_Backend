package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberAchievement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberAchievementRepository extends JpaRepository<MemberAchievement, Long> {

    /** 화면 조회용. achievement 를 같이 끌고 온다 — N+1 방지. */
    @EntityGraph(attributePaths = {"achievement"})
    List<MemberAchievement> findAllByMemberId(Long memberId);

    @EntityGraph(attributePaths = {"achievement"})
    Optional<MemberAchievement> findByMemberIdAndAchievementId(Long memberId, Long achievementId);

    /**
     * 이벤트 처리기가 진행도를 올릴 때 잠근다. 동시에 같은 이벤트가 두 번 들어와도
     * currentProgress 가 한 번만 올라가게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ma from MemberAchievement ma where ma.member.id = :memberId and ma.achievement.id = :achievementId")
    Optional<MemberAchievement> findForUpdate(@Param("memberId") Long memberId,
                                              @Param("achievementId") Long achievementId);

    /**
     * 대표 업적 선택 화면("달성" 탭)용. 배지 이미지가 있고 CLAIMED 상태인 것만 가져온다.
     */
    @Query("""
        select ma from MemberAchievement ma
        join fetch ma.achievement a
        where ma.member.id = :memberId
        and ma.status = com.lirouti.domain.achievement.enums.MemberAchievementStatus.CLAIMED
        and a.badgeImageKey is not null
        order by ma.claimedAt desc
        """)
    List<MemberAchievement> findClaimedWithBadgeByMemberId(@Param("memberId") Long memberId);
}
