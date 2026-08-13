package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.MemberSelectedCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberSelectedCharacterRepository
        extends JpaRepository<MemberSelectedCharacter, Long> {

    boolean existsByMemberId(Long memberId);
}
