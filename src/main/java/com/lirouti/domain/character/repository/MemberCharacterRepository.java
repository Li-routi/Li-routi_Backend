package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.MemberCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberCharacterRepository extends JpaRepository<MemberCharacter, Long> {

    @Query("""
            select memberCharacter.avatarCharacter.id
            from MemberCharacter memberCharacter
            where memberCharacter.member.id = :memberId
            """)
    List<Long> findCharacterIdsByMemberId(@Param("memberId") Long memberId);

    boolean existsByMemberIdAndAvatarCharacterId(Long memberId, Long characterId);
}
