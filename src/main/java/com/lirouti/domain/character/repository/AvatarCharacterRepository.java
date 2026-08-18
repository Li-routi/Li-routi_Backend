package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.AvatarCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvatarCharacterRepository extends JpaRepository<AvatarCharacter, Long> {

    List<AvatarCharacter> findAllByActiveTrueOrderByDisplayOrderAsc();

    /** 도감 화면용. 감춘 캐릭터는 목록에 넣지 않는다. */
    List<AvatarCharacter> findAllByActiveTrueAndHiddenFalseOrderByDisplayOrderAsc();
}
