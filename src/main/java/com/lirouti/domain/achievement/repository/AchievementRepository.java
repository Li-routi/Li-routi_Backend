package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.AchievementProgressType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    Optional<Achievement> findByCode(String code);

    /** 미디어 정리 후보 중 업적이 현재 참조하는 뱃지 이미지 key를 조회한다. */
    @Query("select achievement.badgeImageKey from Achievement achievement "
            + "where achievement.badgeImageKey in :keys")
    List<String> findBadgeImageKeysIn(@Param("keys") Collection<String> keys);

    List<Achievement> findAllByActiveTrueOrderByCategoryAscSortOrderAsc();
    List<Achievement> findAllByActiveTrueAndCategoryOrderBySortOrderAsc(AchievementCategory category);

    /**
     * 이벤트 처리기(AchievementProgressService)가 특정 conditionKey 에 반응해야 할
     * 활성 업적을 모두 찾는다.
     *
     * <p>단일 조건 업적은 자기 자신의 {@code condition_key} 컬럼으로, COMPOSITE 업적은
     * 하위 {@link com.lirouti.domain.achievement.entity.AchievementCondition} 의
     * conditionKey 로 매칭한다 — 두 경로 중 하나만 맞아도 대상에 포함된다.
     *
     * <p>{@code conditions} 를 fetch join 해 오는 이유: COMPOSITE 처리 시 목표치
     * 비교를 위해 어차피 conditions 컬렉션이 필요한데, LAZY 로 두면 이벤트 1건마다
     * N+1 이 난다.
     */
    @EntityGraph(attributePaths = {"conditions"})
    @Query("""
            select distinct a from Achievement a
            left join a.conditions c
            where a.active = true
            and (a.conditionKey = :conditionKey or c.conditionKey = :conditionKey)
            """)
    List<Achievement> findAllActiveByConditionKey(@Param("conditionKey") String conditionKey);

    /**
     * 그룹 단위 파이프라인(GroupAchievementProgressService)이 쓴다. GROUP_CUMULATIVE_COUNT·
     * GROUP_DISTINCT_DAY_COUNT 업적은 {@code condition_key} 를 비워 두므로
     * {@link #findAllActiveByConditionKey} 로는 찾을 수 없다 — progressType 자체로 찾는다.
     */
    List<Achievement> findAllByActiveTrueAndProgressType(AchievementProgressType progressType);
}
