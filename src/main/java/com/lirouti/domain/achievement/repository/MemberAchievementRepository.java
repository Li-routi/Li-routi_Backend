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
     * 대표 업적 선택 화면("달성" 탭)용. <b>배찌로 등록된 업적</b> 중 CLAIMED 인 것만 가져온다.
     *
     * <p>기준은 {@code badge_yn} 이다. 예전에는 "배지 이미지가 있다 + EGG 가 아니다" 로
     * 추론했는데, 그것은 지금 데이터에서 우연히 같은 답을 낼 뿐이다. <b>배찌인지 아닌지는
     * 이미 컬럼으로 있다</b> — 추론하면 새 카테고리가 생기거나 배찌 아닌 업적에 이미지가
     * 붙는 순간 조용히 어긋난다.
     *
     * <p>{@code badgeImageKey is not null} 은 남긴다. 배찌로 표시됐지만 이미지가 아직 안 올라온
     * 업적을 내보내면 화면에 빈 자리가 생긴다 — 그릴 수 없는 것은 후보가 아니다.
     *
     * <p>캐릭터알(EGG)은 배찌가 아니다. {@code badge_yn = 0} 이라 이 조건만으로 빠지고,
     * 대표 업적으로도 고를 수 없다.
     */
    @Query("""
        select ma from MemberAchievement ma
        join fetch ma.achievement a
        where ma.member.id = :memberId
        and ma.status = com.lirouti.domain.achievement.enums.MemberAchievementStatus.CLAIMED
        and a.badgeYn = true
        and a.badgeImageKey is not null
        order by ma.claimedAt desc
        """)
    List<MemberAchievement> findClaimedWithBadgeByMemberId(@Param("memberId") Long memberId);
}
