package com.lirouti.domain.group.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GroupRoutine 테스트")
class GroupRoutineTest {

    @Test
    @DisplayName("요일별 일정을 추가하면 루틴과 양방향 관계가 설정된다")
    void addSchedule_ValidSchedule_AddsWithRoutineReference() {
        // given
        GroupRoutine routine = routine();

        // when
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        // then
        assertThat(routine.getSchedules()).singleElement().satisfies(schedule -> {
            assertThat(schedule.getGroupRoutine()).isSameAs(routine);
            assertThat(schedule.getRepeatDay()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        });
    }

    @Test
    @DisplayName("같은 요일의 일정을 두 번 추가할 수 없다")
    void addSchedule_DuplicateDay_ThrowsIllegalArgumentException() {
        // given
        GroupRoutine routine = routine();
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        // when & then
        assertThatThrownBy(() -> routine.addSchedule(
                DayOfWeek.MONDAY,
                LocalTime.of(18, 0),
                LocalTime.of(19, 0)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종료 시간이 시작 시간과 같거나 빠른 일정은 생성할 수 없다")
    void addSchedule_InvalidTimeRange_ThrowsIllegalArgumentException() {
        // given
        GroupRoutine routine = routine();

        // when & then
        assertThatThrownBy(() -> routine.addSchedule(
                DayOfWeek.TUESDAY,
                LocalTime.of(22, 0),
                LocalTime.of(21, 0)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private GroupRoutine routine() {
        return GroupRoutine.builder()
                .group(mock(Group.class))
                .category(mock(RoutineCategory.class))
                .title("공동 루틴")
                .description("설명")
                .build();
    }
}
