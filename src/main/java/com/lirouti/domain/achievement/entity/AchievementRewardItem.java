package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "achievement_reward_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AchievementRewardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Column(name = "item_category", nullable = false, length = 20)
    private String itemCategory;

    @Column(name = "item_name", nullable = false, length = 50)
    private String itemName;

    @Column(name = "item_ref_id")
    private Long itemRefId;
}
