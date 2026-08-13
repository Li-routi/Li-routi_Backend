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
import java.util.Objects;
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
    @Column(name = "condition_key", length = 40)
    private String conditionKey;

    /**
     * 카테고리 한정 업적이 참조하는 카테고리 목록. {@code achievement_routine_category}
     * 조인 테이블 그대로 매핑한다. 카테고리 1개짜리(운동 시작 등)든 여러 개짜리(건강한
     * 땀방울 = 운동·건강, 루틴 탐험가 = 6개 전부)든 전부 이 컬렉션 하나로 표현한다 — 예전엔
     * 단일 카테고리를 스칼라 FK 컬럼(routine_category_id)으로 따로 뒀었는데, 그 컬럼은
     * {@code V20260812100950} 마이그레이션에서 제거되고 조인 테이블로 완전히 대체됐다.
     *
     * <p>{@link com.lirouti.domain.routine.entity.RoutineCategory} 의 고정 카테고리
     * (owner == null) id 를 가리킨다 — R__seed_routine.sql 이 시드하는 1(운동)~6(취미).
     *
     * <p>비어 있으면 카테고리 무관 업적. 값이 있으면 이벤트의 routineCategoryId 가 이
     * 집합에 포함될 때만 반영한다({@code AchievementProgressService.routineCategoryMatches}
     * 참고) — "전부 다 커버해야" 하는 CATEGORY_COVERAGE_COUNT(루틴 탐험가)는 이 게이트를
     * 통과한 이후, 진행도 반영 단계에서 별도로 "몇 개나 커버했는지"를 판정한다.
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

    @Column(name = "badge_image_key", length = 500)
    private String badgeImageKey;

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
                        Integer targetCount, String conditionKey,
                        int topazReward, boolean badgeYn, boolean limitedOutfitYn,
                        int sortOrder, boolean active, String badgeImageKey) {
        this.code = code;
        this.category = category;
        this.name = name;
        this.conditionDesc = conditionDesc;
        this.progressType = progressType;
        this.targetCount = targetCount;
        this.conditionKey = conditionKey;
        this.topazReward = topazReward;
        this.badgeYn = badgeYn;
        this.badgeImageKey = badgeImageKey;
        this.limitedOutfitYn = limitedOutfitYn;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public void replaceBadgeImageKey(String badgeImageKey) {
        this.badgeImageKey = Objects.requireNonNull(badgeImageKey, "badgeImageKey must not be null");
    }
}
