package com.lirouti.domain.group.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱에서 관리하는 그룹 루틴 카테고리 마스터다.
 * 일반 사용자는 생성하거나 수정할 수 없으며, 활성 카테고리만 신규 루틴에 사용할 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "routine_category",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_routine_category_name", columnNames = "name")
        }
)
public class RoutineCategory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Boolean active;

    /**
     * 앱에서 관리할 루틴 카테고리를 생성한다.
     *
     * @param name 중복되지 않는 카테고리 이름
     * @param active 신규 루틴에서 사용할 수 있는지 여부
     */
    @Builder
    private RoutineCategory(String name, Boolean active) {
        this.name = name;
        this.active = active != null ? active : true;
    }
}
