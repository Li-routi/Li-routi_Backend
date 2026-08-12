package com.lirouti.domain.achievement.entity;

import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.AchievementProgressType;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 업적 정의. 마스터 데이터라 회원마다 달라지지 않는다.
 *
 * <p>보상은 이번 스코프에서 토파즈만 실제로 지급한다. {@code badgeYn}·{@code limitedOutfitYn}
 * 은 상점 도메인이 준비될 때까지 <b>응답 노출용 플래그일 뿐</b> — 지급 로직은 없다.
 */
@Entity
@Getter
@Table(name = "achievement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Achievement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private AchievementCategory category;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "condition_desc", nullable = false, length = 200)
    private String conditionDesc;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_type", nullable = false, length = 30)
    private AchievementProgressType progressType;

    /** 단일 조건 업적의 목표치. COMPOSITE 는 null — {@link AchievementCondition} 을 본다. */
    @Column(name = "target_count")
    private Integer targetCount;

    /**
     * 단일 조건 업적(NONE·CUMULATIVE_COUNT·DISTINCT_ROOM_COUNT)이 반응할 이벤트 키.
     * 예: ROUTINE_COMPLETE_COUNT, LIKE_COUNT, ROOM_JOIN_COUNT.
     *
     * <p>COMPOSITE 는 이 컬럼을 쓰지 않고 {@link AchievementCondition} 의 개별
     * conditionKey 들을 쓴다.
     */
    @Column(name = "condition_key", length = 30)
    private String conditionKey;

    /**
     * 카테고리 시작 업적(운동 시작 등, 카테고리 1개)만 사용하는 단일 FK.
     * {@link com.lirouti.domain.routine.entity.RoutineCategory} 의 고정 카테고리(owner == null)
     * id 를 가리킨다 — R__seed_routine.sql 이 시드하는 1(운동)~6(취미).
     *
     * <p>문자열 코드(EXERCISE 등)가 아니라 FK 로 두는 이유: 카테고리 이름은 자유
     * 텍스트라 바뀔 수 있고, 사용자 카테고리도 같은 테이블에 섞여 있어 이름만으로는
     * 고정 카테고리를 안정적으로 특정할 수 없다.
     *
     * <p>null 이면 카테고리 무관. 카테고리가 2개 이상 걸치거나(예: 건강한 땀방울 = 운동
     * 또는 건강) 6개 전부를 요구하는(루틴 탐험가) 업적은 이 컬럼이 아니라
     * {@link #routineCategoryIds}(조인 테이블)를 쓴다 — 이 둘은 서로 배타적으로 쓰인다.
     */
    @Column(name = "routine_category_id")
    private Long routineCategoryId;

    /**
     * 카테고리가 여러 개 걸치는 업적용 (예: 건강한 땀방울 = 운동·건강, 루틴 탐험가 = 6개 전부).
     * {@code achievement_routine_category} 조인 테이블 그대로 매핑한다.
     *
     * <p>{@link #routineCategoryId}(단일 FK)와 이 컬렉션은 서로 다른 업적이 각각 하나씩만
     * 쓴다 — 같은 업적이 둘 다 채우는 경우는 없다. {@code AchievementProgressService} 의
     * 카테고리 매칭 로직은 두 경로를 모두 확인한다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "achievement_routine_category",
            joinColumns = @JoinColumn(name = "achievement_id")
    )
    @Column(name = "routine_category_id")
    private Set<Long> routineCategoryIds = new HashSet<>();

    @Column(name = "topaz_reward", nullable = false)
    private int topazReward;

    @Column(name = "badge_yn", nullable = false)
    private boolean badgeYn;

    @Column(name = "limited_outfit_yn", nullable = false)
    private boolean limitedOutfitYn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "achievement", fetch = FetchType.LAZY)
    private List<AchievementCondition> conditions = new ArrayList<>();

    @Builder
    private Achievement(String code, AchievementCategory category, String name,
                        String conditionDesc, AchievementProgressType progressType,
                        Integer targetCount, String conditionKey, Long routineCategoryId,
                        int topazReward, boolean badgeYn, boolean limitedOutfitYn,
                        int sortOrder, boolean active) {
        this.code = code;
        this.category = category;
        this.name = name;
        this.conditionDesc = conditionDesc;
        this.progressType = progressType;
        this.targetCount = targetCount;
        this.conditionKey = conditionKey;
        this.routineCategoryId = routineCategoryId;
        this.topazReward = topazReward;
        this.badgeYn = badgeYn;
        this.limitedOutfitYn = limitedOutfitYn;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
