package com.lirouti.domain.badge.entity;

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
 * 업적 달성으로 지급되는 배지. 운영이 채우고 앱은 읽기만 한다.
 *
 * <p>AvatarCharacter와 같은 패턴: id는 시드가 직접 지정한다 — achievement.badge_code가
 * 이 code를 가리키므로 시드 재현성을 위해 자동 증가를 쓰지 않는다.
 *
 * <p>마스터 데이터라 소프트 삭제를 두지 않는다. 내릴 때는 active를 내린다 — 이미 받은
 * 사람의 member_badge 행이 가리키는 대상이 사라지면 안 된다.
 */
@Entity
@Getter
@Table(name = "badge")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Badge extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** 논리 키. achievement.badge_code가 이것을 가리킨다. */
    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** S3 key. 절대 URL이 아니다 — 조회에서 조립한다. */
    @Column(name = "image_key", nullable = false, length = 512)
    private String imageKey;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    private Badge(Long id, String code, String name, String imageKey, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.imageKey = imageKey;
        this.active = active;
    }
}