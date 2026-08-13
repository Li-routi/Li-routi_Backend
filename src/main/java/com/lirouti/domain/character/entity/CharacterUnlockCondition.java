package com.lirouti.domain.character.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐릭터를 여는 조건.
 *
 * <p><b>한 캐릭터에 행이 여럿이면 AND 다.</b> 그래서 "운동 또는 건강" 을 두 행으로 나누면
 * 안 된다 — 둘 다 채워야 하는 뜻이 되어 버린다. 합집합은 한 행의 {@code conditionParam} 에
 * 쉼표로 나열해 표현한다.
 *
 * <p><b>행이 하나도 없으면 항상 열려 있는 기본 캐릭터다.</b> 판정을 붙이기 전에 조건 시드를
 * 먼저 채워야 하는 이유다 — 비워 두면 전부 기본 캐릭터가 된다.
 *
 * <p><b>{@code conditionKey} 를 자바 enum 으로 두지 않는다.</b> 백오피스에서 조건을 걸어
 * 캐릭터를 추가하려는 요구가 있어, 종류가 늘 때 배포 없이 데이터만 넣을 수 있어야 한다.
 * 아는 키가 아니면 판정기가 없다는 뜻이므로 조용히 미달로 본다.
 */
@Entity
@Getter
@Table(
        name = "character_unlock_condition",
        indexes = @Index(
                name = "idx_character_unlock_condition_character",
                columnList = "character_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterUnlockCondition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private AvatarCharacter avatarCharacter;

    @Column(name = "condition_key", nullable = false, length = 30)
    private String conditionKey;

    /**
     * 키마다 뜻이 다르다. 없으면 {@code null}.
     *
     * <p><b>카테고리 조건에는 id 가 아니라 논리 키를 넣는다.</b> 개인·그룹·챌린지 카테고리가
     * 세 체계로 갈라져 id 가 서로 다르고, 운영에는 사용자가 만든 동명 카테고리도 있다.
     */
    @Column(name = "condition_param", length = 255)
    private String conditionParam;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    /** 진행도를 조건별로 표시하기 위한 순서. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private CharacterUnlockCondition(AvatarCharacter avatarCharacter, String conditionKey,
                                     String conditionParam, int targetCount, int sortOrder) {
        this.avatarCharacter = avatarCharacter;
        this.conditionKey = conditionKey;
        this.conditionParam = conditionParam;
        this.targetCount = targetCount;
        this.sortOrder = sortOrder;
    }
}
