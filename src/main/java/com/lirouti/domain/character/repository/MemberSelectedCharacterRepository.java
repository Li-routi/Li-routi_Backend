package com.lirouti.domain.character.repository;

import com.lirouti.domain.character.entity.MemberSelectedCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberSelectedCharacterRepository
        extends JpaRepository<MemberSelectedCharacter, Long> {

    boolean existsByMemberId(Long memberId);

    /** 그룹 화면이 구성원마다 아바타를 그린다 — 사람마다 따로 읽으면 N+1 이 된다. */
    List<MemberSelectedCharacter> findAllByMemberIdIn(List<Long> memberIds);

    Optional<MemberSelectedCharacter> findByMemberId(Long memberId);

    /**
     * 선택을 기록한다. <b>있으면 바꾸고 없으면 만든다.</b>
     *
     * <p>"읽어 보고 없으면 만든다" 로 쓰면 <b>첫 선택에서만</b> 경합이 생긴다. 행이 아직 없는
     * 회원이 두 번 연속 누르면 두 요청이 모두 빈 결과를 받아 각자 INSERT 하고, 진 쪽이 기본
     * 키에 걸려 무결성 오류로 500 이 된다. 같은 것을 두 번 누르는 것이 오류일 이유가 없다는
     * 이 API 의 약속과 어긋난다.
     *
     * <p>제약이 판정하게 두고 진 요청은 갱신으로 흡수한다 — 마지막에 온 것이 이긴다. 착용
     * 저장이 "지금 이 착장으로 해 달라" 를 나중 요청 우선으로 다루는 것과 같은 판단이다.
     *
     * <p>JPA 로는 이 갱신을 표현할 수 없어 네이티브로 둔다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO member_selected_character
                (member_id, character_id, created_at, updated_at)
            VALUES (:memberId, :characterId, NOW(6), NOW(6)) AS new_row
            ON DUPLICATE KEY UPDATE
                character_id = new_row.character_id,
                updated_at   = NOW(6)
            """, nativeQuery = true)
    void upsert(@Param("memberId") Long memberId, @Param("characterId") Long characterId);
}
