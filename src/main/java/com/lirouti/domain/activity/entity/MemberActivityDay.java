package com.lirouti.domain.activity.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 회원이 "이날 루틴을 했다" 는 사실 하나.
 *
 * <p><b>캐릭터 전용이 아니다.</b> 업적·리포트처럼 "얼마나 꾸준히 썼는가" 를 묻는 곳이면
 * 어디서나 쓴다 — 캐릭터가 첫 소비자일 뿐이다. 그래서 캐릭터를 가리키는 칸이 없다.
 *
 * <p><b>쓰기는 이 엔티티로 하지 않는다.</b> 값이 한 방향으로만 움직여야 해서 upsert 가
 * 필요하고, 그것을 JPA 로 표현할 수 없다({@code MemberActivityDayRepository#record} 참고).
 * 이 클래스는 읽기와 스키마 대조를 위해 둔다.
 */
@Entity
@Getter
@Table(
        name = "member_activity_day",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_activity_day",
                columnNames = {"member_id", "activity_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberActivityDay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 루틴을 한 날(KST). */
    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    /**
     * 그날 예정된 <b>개인 루틴</b>을 하나도 빠짐없이 완수했는가.
     *
     * <p>행의 존재와 뜻이 다르다 — 행은 "뭐라도 했다"(개인·그룹·챌린지 합산)이고 이 값은
     * 개인 루틴만 본다. 그룹·챌린지만 한 날은 행이 생기지만 이 값은 {@code false} 다.
     */
    @Column(name = "all_completed", nullable = false)
    private boolean allCompleted;
}
