package com.lirouti.domain.routine.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 루틴 추가 화면에서 카테고리마다 미리 보여 주는 기본 제공 루틴이다.
 *
 * <p>앱이 관리하는 마스터 데이터이므로 사용자가 만들거나 수정할 수 없다.
 * 목록은 R__seed_routine.sql이 단일 진실 공급원이고, 내릴 때는 행을 지우지 않고
 * {@code active}를 false로 바꾼다(database-schema.md의 마스터 데이터 규칙).
 *
 * <p>회원이 이 템플릿을 선택하면 {@link MemberRoutine}이 새로 생기고 그 루틴이
 * 템플릿을 참조한다. 템플릿 자체에는 시간·요일이 없다 — 마감 시각과 반복 요일은
 * 회원이 고르는 값이고 기본값은 각각 23:59과 매일이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "routine_template",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_routine_template_category_name",
                        columnNames = {"category_id", "name"}
                )
        }
)
public class RoutineTemplate extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 기본 루틴이 노출될 카테고리. 고정 카테고리만 기본 루틴을 가진다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private RoutineCategory category;

    @Column(nullable = false, length = 20)
    private String name;

    /** 카테고리 안에서의 노출 순서. 기획이 정한 순서를 시드가 그대로 넣는다. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active;

    /**
     * 기본 제공 루틴을 생성한다. 운영에서는 시드만 이 경로를 사용한다.
     *
     * @param category 노출될 카테고리
     * @param name 카테고리 안에서 중복되지 않는 루틴 이름
     * @param displayOrder 카테고리 안에서의 노출 순서. 미지정 시 0
     * @param active 목록에 노출할지 여부. 미지정 시 {@code true}
     */
    @Builder
    private RoutineTemplate(
            RoutineCategory category,
            String name,
            Integer displayOrder,
            Boolean active
    ) {
        this.category = category;
        this.name = name;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.active = active != null ? active : true;
    }
}
