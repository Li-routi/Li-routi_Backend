package com.lirouti.domain.group.entity;

import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 루틴을 분류하는 카테고리다.
 * 그룹이 없으면 앱이 제공하는 고정 카테고리이고, 그룹이 있으면 해당 그룹 전용 카테고리다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_category",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_routine_category_group_name",
                        columnNames = {"group_id", "name"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_group_routine_category_group_active",
                        columnList = "group_id, active"
                )
        }
)
public class GroupRoutineCategory extends BaseEntity {
    /** 그룹 하나가 추가할 수 있는 사용자 카테고리 수. 고정 카테고리는 포함하지 않는다. */
    public static final int MAX_GROUP_CATEGORY_COUNT = 5;

    /** 그룹 사용자 카테고리 이름 길이 상한. */
    public static final int MAX_GROUP_CATEGORY_NAME_LENGTH = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 그룹. {@code null}이면 앱이 제공하는 고정 그룹 카테고리다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false, length = 100)
    private String name;

    /** 사용자 카테고리의 색상 칩. 고정 카테고리와 색상 미선택은 {@code null}이다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 7)
    private RoutineCategoryColor color;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active;

    @Builder
    private GroupRoutineCategory(
            Group group,
            String name,
            RoutineCategoryColor color,
            Integer displayOrder,
            Boolean active
    ) {
        this.group = group;
        this.name = name;
        this.color = color;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.active = active != null ? active : true;
    }

    /** 앱이 모든 그룹에 제공하는 고정 카테고리인지 확인한다. */
    public boolean isFixed() {
        return group == null;
    }

    /** 고정 카테고리이거나 요청 그룹이 소유한 카테고리인지 확인한다. */
    public boolean isUsableBy(Long groupId) {
        return isFixed() || (groupId != null && groupId.equals(group.getId()));
    }
}
