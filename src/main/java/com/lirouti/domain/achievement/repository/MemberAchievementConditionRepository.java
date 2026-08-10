package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberAchievementConditionRepository extends JpaRepository<MemberAchievementCondition, Long> {

    List<MemberAchievementCondition> findAllByMemberAchievementId(Long memberAchievementId);

    Optional<MemberAchievementCondition> findByMemberAchievementIdAndConditionKey(
            Long memberAchievementId, String conditionKey);
}
