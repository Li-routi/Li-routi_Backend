package com.lirouti.domain.group.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.RoutineCategory;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupConverter 테스트")
class GroupConverterTest {
    @Mock
    private Group group;
    @Mock
    private RoutineCategory category;

    @Test
    @DisplayName("요청을 루틴으로 변환하고 응답 일정은 월요일부터 정렬한다")
    void convert_CreateRoutine_MapsEntityAndSortedResult() {
        // given
        GroupReqDTO.CreateRoutine request = new GroupReqDTO.CreateRoutine(
                3L,
                "저녁 루틴",
                "함께 정리합니다.",
                List.of(
                        schedule(DayOfWeek.FRIDAY, 20, 21),
                        schedule(DayOfWeek.MONDAY, 9, 10)
                )
        );
        when(group.getId()).thenReturn(10L);
        when(category.getId()).thenReturn(3L);
        when(category.getName()).thenReturn("집안일");

        // when
        GroupRoutine routine = GroupConverter.toGroupRoutine(request, group, category);
        ReflectionTestUtils.setField(routine, "id", 100L);
        GroupResDTO.RoutineCreateResult result =
                GroupConverter.toRoutineCreateResult(routine, 2);

        // then
        assertThat(result.routineId()).isEqualTo(100L);
        assertThat(result.groupId()).isEqualTo(10L);
        assertThat(result.categoryId()).isEqualTo(3L);
        assertThat(result.categoryName()).isEqualTo("집안일");
        assertThat(result.title()).isEqualTo("저녁 루틴");
        assertThat(result.assignmentCount()).isEqualTo(2);
        assertThat(result.schedules())
                .extracting(GroupResDTO.RoutineSchedule::repeatDay)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    private GroupReqDTO.RoutineSchedule schedule(DayOfWeek day, int startHour, int endHour) {
        return new GroupReqDTO.RoutineSchedule(
                day,
                LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0)
        );
    }
}
