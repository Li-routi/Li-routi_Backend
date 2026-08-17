package com.lirouti.domain.routine.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;

/**
 * 개인 루틴이 반복되는 한 요일이다.
 *
 * <p>그룹 루틴의 일정({@code group_routine_schedule})과 달리 요일마다 시간 범위를 두지 않는다.
 * 개인 루틴은 마감 시각을 루틴 단위로 한 번만 정하므로(디자인의 설정 바텀시트에도 마감시간이
 * 하나뿐이다) 이 테이블은 "어느 요일에 반복하는가"만 담는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_routine_schedule",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_routine_schedule_day",
                        columnNames = {"member_routine_id", "repeat_day"}
                )
        }
)
public class MemberRoutineSchedule extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_routine_id", nullable = false)
    private MemberRoutine memberRoutine;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_day", nullable = false, length = 9)
    private DayOfWeek repeatDay;

    /**
     * 개인 루틴에 속하는 한 반복 요일을 생성한다.
     *
     * @param memberRoutine 요일이 속한 개인 루틴
     * @param repeatDay 반복 요일
     * @throws IllegalArgumentException 루틴이나 요일이 비어 있는 경우
     */
    @Builder
    private MemberRoutineSchedule(MemberRoutine memberRoutine, DayOfWeek repeatDay) {
        if (memberRoutine == null || repeatDay == null) {
            throw new IllegalArgumentException("개인 루틴과 반복 요일은 필수입니다.");
        }
        this.memberRoutine = memberRoutine;
        this.repeatDay = repeatDay;
    }
}
