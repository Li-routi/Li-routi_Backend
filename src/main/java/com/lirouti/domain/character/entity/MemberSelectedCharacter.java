package com.lirouti.domain.character.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 지금 쓰고 있는 캐릭터.
 *
 * <p><b>보유와 선택은 다른 사실이다.</b> 여러 마리를 모으므로 "어느 것을 쓰고 있는가" 가
 * 따로 있어야 한다.
 *
 * <p><b>{@link MemberCharacter} 에 플래그를 두지 않는다.</b> MySQL 에는 부분 유니크가 없어
 * "회원당 선택 하나" 를 제약으로 만들 수 없다 — 애플리케이션이 지키는 규칙이 되고, 따닥으로
 * 들어온 두 요청이 둘 다 통과하면 선택이 둘이 된다. 표를 나누면 <b>기본 키가 그것을 공짜로
 * 보장한다.</b>
 *
 * <p><b>잠긴 캐릭터를 고를 수 없다는 것도 제약이 막는다.</b> 복합 외래 키가
 * {@code member_character(member_id, character_id)} 를 참조하므로 보유 행이 없으면 INSERT
 * 자체가 실패한다 — 알 상태는 고를 수 없다.
 *
 * <p><b>연관을 걸지 않고 id 로 둔다.</b> 두 컬럼이 하나의 복합 외래 키로 묶여 있어, 각각을
 * {@code @ManyToOne} 으로 매핑하면 JPA 가 서로 다른 두 참조로 보고 그 묶임을 표현하지
 * 못한다. 읽을 일이 생기면 id 로 조회한다.
 */
@Entity
@Getter
@Table(name = "member_selected_character")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSelectedCharacter extends BaseEntity {

    /** 회원 하나에 선택 하나. 기본 키가 그것을 강제한다. */
    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Builder
    private MemberSelectedCharacter(Long memberId, Long characterId) {
        this.memberId = memberId;
        this.characterId = characterId;
    }
}
