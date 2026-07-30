package com.lirouti.domain.group.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lirouti.domain.routine.entity.RoutineCategory;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
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

    @Test
    @DisplayName("루틴 기본 정보를 변경한다")
    void update_ValidValues_ChangesRoutineDetails() {
        // given
        GroupRoutine routine = routine();
        RoutineCategory changedCategory = mock(RoutineCategory.class);

        // when
        routine.update(changedCategory, "변경 루틴", "변경된 설명");

        // then
        assertThat(routine.getCategory()).isSameAs(changedCategory);
        assertThat(routine.getTitle()).isEqualTo("변경 루틴");
        assertThat(routine.getDescription()).isEqualTo("변경된 설명");
    }

    @Test
    @DisplayName("반복 일정 교체는 같은 요일을 갱신하고 빠진 요일을 제거하며 새 요일을 추가한다")
    void replaceSchedules_ValidReplacement_SynchronizesByDay() {
        // given
        GroupRoutine routine = routine();
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        routine.addSchedule(DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));
        GroupRoutineSchedule monday = routine.getSchedules().getFirst();

        // when
        routine.replaceSchedules(List.of(
                new GroupRoutine.ScheduleUpdate(
                        DayOfWeek.MONDAY,
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0)
                ),
                new GroupRoutine.ScheduleUpdate(
                        DayOfWeek.WEDNESDAY,
                        LocalTime.of(20, 0),
                        LocalTime.of(21, 0)
                )
        ));

        // then
        assertThat(routine.getSchedules())
                .extracting(GroupRoutineSchedule::getRepeatDay)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(routine.getSchedules())
                .filteredOn(schedule -> schedule.getRepeatDay() == DayOfWeek.MONDAY)
                .singleElement()
                .isSameAs(monday)
                .satisfies(schedule -> {
                    assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(10, 0));
                    assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(11, 0));
                });
        assertThat(routine.getSchedules())
                .filteredOn(schedule -> schedule.getRepeatDay() == DayOfWeek.WEDNESDAY)
                .singleElement()
                .extracting(GroupRoutineSchedule::getGroupRoutine)
                .isSameAs(routine);
    }

    @Test
    @DisplayName("잘못된 교체 일정은 기존 일정을 부분 변경하지 않는다")
    void replaceSchedules_InvalidReplacement_KeepsExistingSchedules() {
        // given
        GroupRoutine routine = routine();
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        // when & then
        assertThatThrownBy(() -> routine.replaceSchedules(List.of(
                new GroupRoutine.ScheduleUpdate(
                        DayOfWeek.TUESDAY,
                        LocalTime.of(11, 0),
                        LocalTime.of(10, 0)
                )
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThat(routine.getSchedules()).singleElement().satisfies(schedule -> {
            assertThat(schedule.getRepeatDay()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        });
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
