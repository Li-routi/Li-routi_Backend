package com.lirouti.domain.routine.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 회원이 혼자 수행하는 개인 루틴이다. 그룹 루틴({@code group_routine})과 달리 담당자가 본인 한 명이다.
 *
 * <p>기본 제공 루틴({@link RoutineTemplate})을 고른 루틴과 회원이 직접 추가한 루틴을 같은
 * 테이블에 둔다. 구분은 {@code template}의 존재 여부다.
 *
 * <p>기획의 "이름을 바꾸면 원본 선택이 해제된다"는 규칙을 생성자에서 지킨다.
 * 기본 루틴을 골라 놓고 이름만 고치면 더 이상 그 기본 루틴을 고른 상태가 아니어야 하므로
 * ({@code 루틴 추가} 화면의 체크가 풀린다) {@code template} 참조를 떼고 사용자 루틴으로 저장한다.
 * 이름을 그대로 둔 채 마감 시각·요일·알람만 바꾼 경우에는 참조를 유지한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_routine",
        uniqueConstraints = {
                // 같은 기본 루틴을 두 번 고를 수 없다. 화면의 체크박스는 한 번만 켜지지만,
                // 같은 요청이 두 번 도착하면(재시도·더블탭) 애플리케이션 검증만으로는 막을 수 없다.
                // routine_template_id가 NULL인 직접 추가 루틴은 MySQL이 NULL을 서로 다른 값으로
                // 취급하므로 이 제약에 걸리지 않는다 — 사용자 루틴끼리 같은 이름을 허용하는 기획과 맞다.
                @UniqueConstraint(
                        name = "uk_member_routine_member_template",
                        columnNames = {"member_id", "routine_template_id"}
                )
        }
)
public class MemberRoutine extends BaseEntity {
    /** 한 회원이 동시에 가질 수 있는 활성 개인 루틴 수. */
    public static final int MAX_ACTIVE_COUNT = 30;

    /** 루틴 이름 길이 상한. 앞뒤 공백을 제거한 뒤의 길이를 센다. */
    public static final int MAX_NAME_LENGTH = 20;

    /** 마감 시각을 지정하지 않았을 때 쓰는 기본값. 그날 안에 하면 되는 루틴이라는 뜻이다. */
    public static final LocalTime DEFAULT_END_TIME = LocalTime.of(23, 59);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 루틴이 소속된 카테고리. 고정 카테고리이거나 이 회원이 만든 사용자 카테고리다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private RoutineCategory category;

    /** 원본 기본 제공 루틴. 직접 추가했거나 이름을 바꾼 루틴은 {@code null}이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_template_id")
    private RoutineTemplate template;

    @Column(nullable = false, length = 20)
    private String name;

    /** 그날의 마감 시각. 시작 시각은 두지 않는다 — 개인 루틴은 마감만 정한다. */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** 알람을 시작할 시각. "없음"이면 {@code null}이고 알림을 보내지 않는다. */
    @Column(name = "alarm_time")
    private LocalTime alarmTime;

    @Column(nullable = false)
    private Boolean active;

    @OneToMany(mappedBy = "memberRoutine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberRoutineSchedule> schedules = new ArrayList<>();

    /**
     * 검증된 회원·카테고리에 속하는 개인 루틴을 생성한다.
     * 기본 루틴을 골랐지만 이름이 원본과 다르면 원본 참조를 떼고 사용자 루틴으로 만든다.
     *
     * @param member 소유 회원
     * @param category 루틴이 소속될 카테고리
     * @param template 선택한 기본 제공 루틴. 직접 추가한 루틴이면 {@code null}
     * @param name 앞뒤 공백을 제거한 1~{@value #MAX_NAME_LENGTH}자의 루틴 이름
     * @param endTime 마감 시각. 미지정 시 {@link #DEFAULT_END_TIME}
     * @param alarmTime 알람 시각. 선택하지 않았으면 {@code null}
     * @param active 활성 여부. 미지정 시 {@code true}
     * @throws IllegalArgumentException 회원·카테고리·이름이 비었거나 이름 길이가 규칙을 벗어난 경우
     */
    @Builder
    private MemberRoutine(
            Member member,
            RoutineCategory category,
            RoutineTemplate template,
            String name,
            LocalTime endTime,
            LocalTime alarmTime,
            Boolean active
    ) {
        if (member == null || category == null) {
            throw new IllegalArgumentException("회원과 카테고리는 필수입니다.");
        }
        validateName(name);
        this.member = member;
        this.category = category;
        this.template = keepsTemplateName(template, name) ? template : null;
        this.name = name;
        this.endTime = endTime != null ? endTime : DEFAULT_END_TIME;
        this.alarmTime = alarmTime;
        this.active = active != null ? active : true;
    }

    /**
     * 한 반복 요일을 추가하고 루틴과 일정의 양방향 관계를 설정한다.
     *
     * @param repeatDay 반복 요일
     * @throws IllegalArgumentException 같은 요일이 이미 등록된 경우
     */
    public void addSchedule(DayOfWeek repeatDay) {
        Objects.requireNonNull(repeatDay, "반복 요일은 필수입니다.");
        if (schedules.stream().anyMatch(schedule -> schedule.getRepeatDay() == repeatDay)) {
            throw new IllegalArgumentException("같은 요일을 중복해서 등록할 수 없습니다.");
        }
        schedules.add(MemberRoutineSchedule.builder()
                .memberRoutine(this)
                .repeatDay(repeatDay)
                .build());
    }

    /**
     * 개인 루틴 설정을 전체 교체한다.
     * 이름이 원본 기본 루틴과 달라지면 원본 참조를 해제하고 사용자 루틴으로 전환한다.
     */
    public void update(
            String name,
            LocalTime endTime,
            LocalTime alarmTime,
            List<DayOfWeek> repeatDays
    ) {
        validateName(name);
        if (endTime == null) {
            throw new IllegalArgumentException("마감 시각은 필수입니다.");
        }
        if (repeatDays == null
                || repeatDays.isEmpty()
                || repeatDays.stream().anyMatch(Objects::isNull)
                || new HashSet<>(repeatDays).size() != repeatDays.size()) {
            throw new IllegalArgumentException("반복 요일은 하나 이상이며 중복될 수 없습니다.");
        }

        this.template = keepsTemplateName(this.template, name) ? this.template : null;
        this.name = name;
        this.endTime = endTime;
        this.alarmTime = alarmTime;
        this.schedules.clear();
        repeatDays.forEach(this::addSchedule);
    }

    /** 반복 일정 전체 교체 전에 기존 행을 먼저 삭제할 수 있도록 컬렉션을 비운다. */
    public void clearSchedules() {
        this.schedules.clear();
    }

    /**
     * 개인 루틴을 비활성화한다.
     * 원본 참조를 해제해 같은 기본 루틴을 다시 등록할 수 있게 하고 반복 일정도 정리한다.
     */
    public void deactivate() {
        this.active = false;
        this.template = null;
        clearSchedules();
    }

    /**
     * 기본 제공 루틴에서 만들어진 루틴인지 판단한다.
     *
     * @return 원본 기본 루틴을 참조하고 있으면 {@code true}
     */
    public boolean isFromTemplate() {
        return template != null;
    }

    /** 선택한 기본 루틴의 이름을 그대로 유지했는지 판단한다. 원본이 없으면 유지 대상도 없다. */
    private static boolean keepsTemplateName(RoutineTemplate template, String name) {
        return template != null && template.getName().equals(name);
    }

    private static void validateName(String name) {
        if (name == null
                || name.isBlank()
                || name.length() > MAX_NAME_LENGTH
                || name.indexOf('\n') >= 0
                || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "루틴 이름은 1자 이상 " + MAX_NAME_LENGTH + "자 이하여야 합니다.");
        }
    }
}
