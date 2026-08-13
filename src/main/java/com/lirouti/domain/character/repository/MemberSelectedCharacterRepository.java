package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.MemberSelectedCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberSelectedCharacterRepository
        extends JpaRepository<MemberSelectedCharacter, Long> {

    boolean existsByMemberId(Long memberId);

    /** 그룹 화면이 구성원마다 아바타를 그린다 — 사람마다 따로 읽으면 N+1 이 된다. */
    List<MemberSelectedCharacter> findAllByMemberIdIn(List<Long> memberIds);

    Optional<MemberSelectedCharacter> findByMemberId(Long memberId);
}
