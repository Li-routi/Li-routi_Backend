package com.lirouti.domain.mypage.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건의를 등록할 때 고르는 분류. <b>앱이 제공하는 마스터 데이터다.</b>
 *
 * <p>{@code R__} 시드가 단일 진실 공급원이고 id 는 시드가 직접 지정한다 — 자동 증가가 아니다.
 *
 * <p><b>더 이상 받지 않을 분류는 지우지 않고 {@code active = false} 로 내린다.</b> 지우면 그
 * 분류로 이미 보낸 건의가 참조를 잃는다.
 */
@Entity
@Getter
@Table(
        name = "suggestion_category",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_suggestion_category_code", columnNames = "code")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuggestionCategory extends BaseEntity {

    /** 시드가 지정한다. {@code @GeneratedValue} 를 두지 않는 이유다. */
    @Id
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;
}
