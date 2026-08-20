package com.lirouti.domain.routine.entity;

import com.lirouti.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MemberRoutine 테스트")
class MemberRoutineTest {
    private static final String TEMPLATE_NAME = "물 챙겨 마시기";

    @Test
    @DisplayName("기본 루틴 이름을 그대로 두면 원본 선택이 유지된다")
    void create_TemplateNameKept_KeepsTemplateReference() {
        // given
        RoutineTemplate template = template();

        // when
        MemberRoutine routine = routine(template, TEMPLATE_NAME);

        // then
        assertAll(
                () -> assertThat(routine.isFromTemplate()).isTrue(),
                () -> assertThat(routine.getTemplate()).isSameAs(template)
        );
    }

    @Test
    @DisplayName("기본 루틴 이름을 바꾸면 원본 선택이 해제되고 사용자 루틴이 된다")
    void create_TemplateNameChanged_DetachesTemplateReference() {
        // given
        RoutineTemplate template = template();

        // when
        MemberRoutine routine = routine(template, "물 2L 마시기");

        // then
        assertAll(
                () -> assertThat(routine.isFromTemplate()).isFalse(),
                () -> assertThat(routine.getTemplate()).isNull(),
                () -> assertThat(routine.getName()).isEqualTo("물 2L 마시기")
        );
    }

    @Test
    @DisplayName("마감 시각을 지정하지 않으면 23:59로 저장한다")
    void create_NoEndTime_UsesDefaultEndTime() {
        // given & when
        MemberRoutine routine = MemberRoutine.builder()
                .member(mock(Member.class))
                .category(mock(RoutineCategory.class))
                .name("직접 추가")
                .build();

        // then
        assertAll(
                () -> assertThat(routine.getEndTime()).isEqualTo(LocalTime.of(23, 59)),
                () -> assertThat(routine.getAlarmTime()).isNull(),
                () -> assertThat(routine.getActive()).isTrue()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("이름이 비어 있으면 생성에 실패한다")
    void create_BlankName_ThrowsIllegalArgument(String name) {
        // given & when & then
        assertThatThrownBy(() -> buildWithName(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("루틴 이름");
    }

    @Test
    @DisplayName("이름이 20자를 넘으면 생성에 실패한다")
    void create_TooLongName_ThrowsIllegalArgument() {
        // given
        String tooLong = "가".repeat(MemberRoutine.MAX_NAME_LENGTH + 1);

        // when & then
        assertThatThrownBy(() -> buildWithName(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("루틴 이름");
    }

    @Test
    @DisplayName("이름이 정확히 20자면 생성에 성공한다")
    void create_MaxLengthName_Succeeds() {
        // given
        String maxLength = "가".repeat(MemberRoutine.MAX_NAME_LENGTH);

        // when
        MemberRoutine routine = buildWithName(maxLength);

        // then
        assertThat(routine.getName()).isEqualTo(maxLength);
    }

    @Test
    @DisplayName("반복 요일을 추가하면 루틴과 양방향 관계가 설정된다")
    void addSchedule_ValidDay_AddsWithRoutineReference() {
        // given
        MemberRoutine routine = routine(null, "직접 추가");

        // when
        routine.addSchedule(DayOfWeek.MONDAY);

        // then
        assertAll(
                () -> assertThat(routine.getSchedules()).hasSize(1),
                () -> assertThat(routine.getSchedules().getFirst().getRepeatDay())
                        .isEqualTo(DayOfWeek.MONDAY),
                () -> assertThat(routine.getSchedules().getFirst().getMemberRoutine())
                        .isSameAs(routine)
        );
    }

    @Test
    @DisplayName("같은 요일을 두 번 추가하면 실패한다")
    void addSchedule_DuplicateDay_ThrowsIllegalArgument() {
        // given
        MemberRoutine routine = routine(null, "직접 추가");
        routine.addSchedule(DayOfWeek.MONDAY);

        // when & then
        assertThatThrownBy(() -> routine.addSchedule(DayOfWeek.MONDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("이름을 유지한 수정은 원본 참조를 유지하고 반복 요일을 교체한다")
    void update_KeptTemplateName_KeepsTemplateAndReplacesSettings() {
        RoutineTemplate template = template();
        MemberRoutine routine = routine(template, TEMPLATE_NAME);
        routine.addSchedule(DayOfWeek.MONDAY);

        routine.update(
                TEMPLATE_NAME,
                null,
                LocalTime.of(21, 30),
                LocalTime.of(20, 30),
                List.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        );

        assertAll(
                () -> assertThat(routine.getTemplate()).isSameAs(template),
                () -> assertThat(routine.getStartTime()).isNull(),
                () -> assertThat(routine.getEndTime()).isEqualTo(LocalTime.of(21, 30)),
                () -> assertThat(routine.getAlarmTime()).isEqualTo(LocalTime.of(20, 30)),
                () -> assertThat(routine.getSchedules())
                        .extracting(MemberRoutineSchedule::getRepeatDay)
                        .containsExactly(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        );
    }

    @Test
    @DisplayName("이름을 바꾼 수정은 원본 참조를 해제한다")
    void update_ChangedTemplateName_DetachesTemplate() {
        MemberRoutine routine = routine(template(), TEMPLATE_NAME);

        routine.update(
                "물 2L 마시기",
                null,
                LocalTime.of(22, 0),
                null,
                List.of(DayOfWeek.MONDAY)
        );

        assertAll(
                () -> assertThat(routine.getTemplate()).isNull(),
                () -> assertThat(routine.getName()).isEqualTo("물 2L 마시기"),
                () -> assertThat(routine.getStartTime()).isNull(),
                () -> assertThat(routine.getAlarmTime()).isNull()
        );
    }

    @Test
    @DisplayName("삭제는 루틴을 비활성화하고 원본과 반복 일정을 해제한다")
    void deactivate_DetachesTemplateAndClearsSchedules() {
        MemberRoutine routine = routine(template(), TEMPLATE_NAME);
        routine.addSchedule(DayOfWeek.MONDAY);

        routine.deactivate();

        assertAll(
                () -> assertThat(routine.getActive()).isFalse(),
                () -> assertThat(routine.getTemplate()).isNull(),
                () -> assertThat(routine.getSchedules()).isEmpty()
        );
    }

    private MemberRoutine buildWithName(String name) {
        return MemberRoutine.builder()
                .member(mock(Member.class))
                .category(mock(RoutineCategory.class))
                .name(name)
                .build();
    }

    private RoutineTemplate template() {
        RoutineTemplate template = mock(RoutineTemplate.class);
        when(template.getName()).thenReturn(TEMPLATE_NAME);
        return template;
    }

    private MemberRoutine routine(RoutineTemplate template, String name) {
        return MemberRoutine.builder()
                .member(mock(Member.class))
                .category(mock(RoutineCategory.class))
                .template(template)
                .name(name)
                .endTime(LocalTime.of(22, 0))
                .build();
    }
}
