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
 * 아바타가 입는 몸. <b>운영이 채우고 앱은 읽기만 한다.</b>
 *
 * <p><b>파는 것이 아니라 조건으로 열리는 것</b>이라 아이템과 표를 나눈다. 한 표에 두면
 * 돈으로 사는 것과 조건으로 여는 것이 섞이고, 슬롯 유니크가 "캐릭터는 반드시 하나" 와
 * "아이템은 없을 수 있다" 를 같은 규칙으로 다루게 된다.
 *
 * <p><b>표 이름이 {@code character} 가 아니다.</b> MySQL 예약어라 백틱 없이는 쓸 수 없고,
 * 자바에서도 {@code java.lang.Character} 와 이름이 겹친다. 자산 prefix 가 이미
 * {@code avatar/} 하나로 모여 있어 이름도 그쪽에 맞췄다.
 *
 * <p>마스터라 소프트 삭제를 두지 않는다. 내릴 때는 {@code active} 를 내린다 — 이미 해금한
 * 사람의 보유 행이 가리키는 대상이 사라지면 안 된다.
 */
@Entity
@Getter
@Table(name = "avatar_character")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvatarCharacter extends BaseEntity {

    /**
     * 시드가 직접 지정한다.
     *
     * <p>자동 증가를 쓰지 않는 이유는 조건·자산이 이 값을 가리키기 때문이다 — 환경마다
     * 번호가 달라지면 시드가 재현되지 않는다.
     */
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** 논리 키. 시드와 조건이 이것으로 서로를 가리킨다. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    /**
     * 잠긴 상태로 보여줄 알 그림. <b>해금 뒤에는 쓰지 않는다</b> — 목록의 잠금 표시 전용이다.
     *
     * <p>절대 URL 이 아니라 S3 key 다. 조회에서 조립한다.
     */
    @Column(name = "egg_image_key", nullable = false, length = 512)
    private String eggImageKey;

    /** 해금 뒤 보여줄 성체 그림. 마찬가지로 key 다. */
    @Column(name = "adult_image_key", nullable = false, length = 512)
    private String adultImageKey;

    /** 목록에 감출지. 지금 시드는 전부 {@code false} 다 — 히든 캐릭터를 두지 않는다. */
    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    private AvatarCharacter(Long id, String code, String name, String eggImageKey,
                            String adultImageKey, boolean hidden, int displayOrder,
                            boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.eggImageKey = eggImageKey;
        this.adultImageKey = adultImageKey;
        this.hidden = hidden;
        this.displayOrder = displayOrder;
        this.active = active;
    }
}
