package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.CharacterUnlockCondition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharacterUnlockConditionRepository extends JpaRepository<CharacterUnlockCondition, Long> {

    /**
     * "이 achievement 코드로 열리는 캐릭터가 있는가"를 찾는다. conditionKey가
     * ACHIEVEMENT_CLAIMED이고 conditionParam이 이 achievementCode인 행들.
     *
     * <p>avatarCharacter를 fetch join한다 — 뒤에서 바로 characterId가 필요하다.
     */
    @EntityGraph(attributePaths = {"avatarCharacter"})
    @Query("""
            select c from CharacterUnlockCondition c
            where c.conditionKey = 'ACHIEVEMENT_CLAIMED'
            and c.conditionParam = :achievementCode
            """)
    List<CharacterUnlockCondition> findByAchievementCode(@Param("achievementCode") String achievementCode);
}