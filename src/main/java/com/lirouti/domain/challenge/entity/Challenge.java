package com.lirouti.domain.challenge.entity;

import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱이 제공하는 챌린지 마스터. 회원이 직접 생성할 수 없다.
 * 노출 여부는 active로 제어하며 소프트 삭제는 쓰지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "challenge")
public class Challenge extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    // 목록·상세 카드에 표시할 대표 이미지. 없을 수 있어 nullable(프론트가 기본 아이콘 표시).
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChallengeCategory category;

    // 인증 주기. 현재 데이터는 전부 DAILY. 스트릭 셈법도 DAILY 기준으로만 구현한다.
    // columnDefinition의 default는 ddl-auto=update가 기존 챌린지 행에 이 컬럼을 추가할 때
    // 유효한 값(DAILY)으로 채우기 위함이다(default 없으면 ''로 채워져 enum 매핑이 깨진다).
    @Enumerated(EnumType.STRING)
    @Column(name = "routine_cycle", nullable = false, columnDefinition = "varchar(20) default 'DAILY'")
    private RoutineCycle routineCycle;

    // 챌린지 성공 시 지급할 무료 재화 수량. 지금은 보관만 하며 실제 적립·성공 정의는 미구현.
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer reward;

    @Column(nullable = false)
    private Boolean active;

    @Builder
    private Challenge(
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category,
            RoutineCycle routineCycle,
            Integer reward,
            Boolean active
    ) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.category = category;
        this.routineCycle = (routineCycle != null) ? routineCycle : RoutineCycle.DAILY;
        this.reward = (reward != null) ? reward : 0;
        this.active = (active != null) ? active : true;
    }
}
