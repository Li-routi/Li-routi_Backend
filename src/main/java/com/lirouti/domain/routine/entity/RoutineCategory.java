package com.lirouti.domain.routine.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 루틴을 분류하는 카테고리다. 개인 루틴과 그룹 루틴이 같은 마스터를 공유한다.
 *
 * <p>두 종류가 한 테이블에 있고 {@code owner}로 구분한다.
 * <ul>
 *   <li>고정 카테고리 — {@code owner}가 {@code null}이다. 앱이 시드로 관리하며(R__seed_routine.sql)
 *       모든 회원에게 같은 id로 보인다. 사용자가 만들거나 지울 수 없다.</li>
 *   <li>사용자 카테고리 — {@code owner}가 있는 회원 소유다. 회원당 최대
 *       {@link #MAX_MEMBER_CATEGORY_COUNT}개까지 추가할 수 있다.</li>
 * </ul>
 *
 * <p>테이블을 나누지 않은 것은 의도적이다. 루틴은 두 종류 중 어느 쪽에도 속할 수 있어서,
 * 나누면 {@code member_routine}이 카테고리 종류에 따라 다른 FK를 갖는 구조가 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "routine_category",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_routine_category_member_name",
                        columnNames = {"member_id", "name"}
                )
        }
)
public class RoutineCategory extends BaseEntity {
    /** 회원 한 명이 추가할 수 있는 사용자 카테고리 수. 고정 카테고리는 포함하지 않는다. */
    public static final int MAX_MEMBER_CATEGORY_COUNT = 5;

    /** 사용자 카테고리 이름 길이 상한. 고정 카테고리 이름보다 짧게 제한한다. */
    public static final int MAX_MEMBER_CATEGORY_NAME_LENGTH = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 회원. {@code null}이면 앱이 제공하는 고정 카테고리다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member owner;

    @Column(nullable = false, length = 100)
    private String name;

    /** 사용자 카테고리의 색상 칩. 고정 카테고리와 "색 없음"은 {@code null}이다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 7)
    private RoutineCategoryColor color;

    /**
     * 목록에서의 노출 순서. 고정 카테고리는 시드가 정한 기획 순서를 그대로 쓰고,
     * 사용자 카테고리는 모두 0이라 생성 순서(id)로 정렬된다.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active;

    /**
     * 카테고리를 생성한다. 고정 카테고리는 시드로만 만들어지므로 이 생성자는
     * 실질적으로 사용자 카테고리 생성 경로다.
     *
     * @param owner 소유 회원. {@code null}이면 고정 카테고리
     * @param name 소유자 범위에서 중복되지 않는 카테고리 이름
     * @param color 색상 칩. 선택하지 않았으면 {@code null}
     * @param displayOrder 노출 순서. 미지정 시 0
     * @param active 신규 루틴에서 사용할 수 있는지 여부. 미지정 시 {@code true}
     */
    @Builder
    private RoutineCategory(
            Member owner,
            String name,
            RoutineCategoryColor color,
            Integer displayOrder,
            Boolean active
    ) {
        this.owner = owner;
        this.name = name;
        this.color = color;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.active = active != null ? active : true;
    }

    /**
     * 앱이 모든 회원에게 동일하게 제공하는 고정 카테고리인지 판단한다.
     *
     * @return 소유 회원이 없으면 {@code true}
     */
    public boolean isFixed() {
        return owner == null;
    }

    /**
     * 주어진 회원이 이 카테고리를 루틴에 사용할 수 있는지 판단한다.
     * 고정 카테고리는 모두에게 열려 있고, 사용자 카테고리는 소유자만 쓸 수 있다.
     *
     * @param memberId 사용 여부를 확인할 회원 ID
     * @return 고정 카테고리이거나 해당 회원이 소유자면 {@code true}
     */
    public boolean isUsableBy(Long memberId) {
        return isFixed() || (memberId != null && memberId.equals(owner.getId()));
    }

    /** 주어진 회원이 직접 만든 사용자 카테고리인지 확인한다. */
    public boolean isOwnedBy(Long memberId) {
        return !isFixed() && memberId != null && memberId.equals(owner.getId());
    }

    /** 사용자 카테고리의 이름과 색상을 교체한다. */
    public void update(String name, RoutineCategoryColor color) {
        if (isFixed()) {
            throw new IllegalStateException("고정 카테고리는 수정할 수 없습니다.");
        }
        if (name == null
                || name.isBlank()
                || name.length() > MAX_MEMBER_CATEGORY_NAME_LENGTH
                || name.indexOf('\n') >= 0
                || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "카테고리 이름은 1자 이상 " + MAX_MEMBER_CATEGORY_NAME_LENGTH
                            + "자 이하여야 하며 줄바꿈을 포함할 수 없습니다.");
        }
        this.name = name;
        this.color = color;
    }
}
