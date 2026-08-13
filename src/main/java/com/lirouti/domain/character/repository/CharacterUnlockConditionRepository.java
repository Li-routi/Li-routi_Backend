package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.CharacterUnlockCondition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharacterUnlockConditionRepository extends JpaRepository<CharacterUnlockCondition, Long> {

    /**
     * "이 achievement 코드를 claim하면 열리는 캐릭터가 있는가"를 찾는다. conditionKey가
     * ACHIEVEMENT_CLAIMED이고 conditionParam이 이 achievementCode인 행들.
     *
     * <p>avatarCharacter를 fetch join한다 - 곧바로 characterId가 필요하다.
     */
    @EntityGraph(attributePaths = {"avatarCharacter"})
    @Query("""
            select c from CharacterUnlockCondition c
            where c.conditionKey = 'ACHIEVEMENT_CLAIMED'
            and c.conditionParam = :achievementCode
            """)
    List<CharacterUnlockCondition> findByAchievementCode(@Param("achievementCode") String achievementCode);

    /**
     * <b>활성 캐릭터의</b> 조건 전부를 캐릭터와 함께 한 번에 읽는다.
     *
     * <p>내린 캐릭터(active = false)의 조건은 아예 읽지 않는다 — 이름에 그 필터가 드러나야
     * 호출하는 쪽이 "전부" 로 오해하지 않는다.
     *
     * <p>마스터라 열두 행뿐이고, 판정할 때마다 캐릭터별로 나눠 읽으면 N+1 이 된다.
     */
    @Query("""
            select condition
            from CharacterUnlockCondition condition
            join fetch condition.avatarCharacter avatarCharacter
            where avatarCharacter.active = true
            order by condition.sortOrder asc, condition.id asc
            """)
    List<CharacterUnlockCondition> findAllOfActiveCharacters();
}