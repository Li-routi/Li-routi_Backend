package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.AvatarCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvatarCharacterRepository extends JpaRepository<AvatarCharacter, Long> {

    List<AvatarCharacter> findAllByActiveTrueOrderByDisplayOrderAsc();
}
