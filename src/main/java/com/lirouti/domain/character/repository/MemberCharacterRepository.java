package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.MemberCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

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

    @Query("""
            select memberCharacter.avatarCharacter.id
            from MemberCharacter memberCharacter
            where memberCharacter.member.id = :memberId
            """)
    List<Long> findCharacterIdsByMemberId(@Param("memberId") Long memberId);

    boolean existsByMemberIdAndAvatarCharacterId(Long memberId, Long characterId);

    /** 목록 화면이 캐릭터마다 보유를 묻지 않도록 한 번에 읽는다. */
    List<MemberCharacter> findAllByMemberId(Long memberId);

    /**
     * 회원마다 가장 먼저 얻은 캐릭터. <b>선택이 비어 있을 때의 대체값</b>이다.
     *
     * <p>업적 claim 으로 캐릭터를 받으면 보유만 생기고 선택은 비어 있다. 그대로 두면 아바타에
     * 캐릭터가 아예 안 그려지므로, 화면은 가장 먼저 얻은 것으로 대신 그린다.
     *
     * @return {@code [memberId, characterId]} 배열의 목록
     */
    @Query("""
            select memberCharacter.member.id, memberCharacter.avatarCharacter.id
            from MemberCharacter memberCharacter
            where memberCharacter.member.id in :memberIds
            order by memberCharacter.id asc
            """)
    List<Object[]> findFirstOwnedByMemberIds(@Param("memberIds") List<Long> memberIds);
}
