package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.MemberCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MemberCharacterRepository extends JpaRepository<MemberCharacter, Long> {

    /**
     * @return 신규 해금이면 1, 이미 보유 중이면(unique 위반, no-op) 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO member_character (member_id, character_id, unlocked_date, created_at, updated_at)
        VALUES (:memberId, :characterId, :unlockedDate, NOW(), NOW())
        ON DUPLICATE KEY UPDATE id = id
        """, nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId,
                       @Param("characterId") Long characterId,
                       @Param("unlockedDate") LocalDate unlockedDate);
}