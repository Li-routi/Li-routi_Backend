package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberAchievementConditionRepository extends JpaRepository<MemberAchievementCondition, Long> {

    List<MemberAchievementCondition> findAllByMemberAchievementId(Long memberAchievementId);

    Optional<MemberAchievementCondition> findByMemberAchievementIdAndConditionKey(
            Long memberAchievementId, String conditionKey);

    /**
     * 업적 목록 화면용.
     * 복합 조건 업적은 한 번에 묶어 가져온다
     */
    List<MemberAchievementCondition> findAllByMemberAchievementIdIn(Collection<Long> memberAchievementIds);
}
