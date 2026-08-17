package com.lirouti.domain.routine.service.query;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import com.lirouti.domain.routine.repository.RoutineTemplateRepository;
import com.lirouti.global.util.TimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutineQueryService 개인 루틴 목록 테스트")
class RoutineQueryServiceTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private RoutineCategoryRepository routineCategoryRepository;
    @Mock
    private RoutineTemplateRepository routineTemplateRepository;
    @Mock
    private MemberRoutineRepository memberRoutineRepository;
    @Mock
    private RoutineCompletionSource completionSource;

    @InjectMocks
    private RoutineQueryService routineQueryService;

    @Test
    @DisplayName("노출 순서를 유지하며 반복 일정과 오늘 완료 상태를 묶음 조회한다")
    void getRoutines_PreservesDisplayOrderAndLoadsSchedulesInBatch() {
        Member member = mock(Member.class);
        RoutineCategory exercise = category(1L, "운동");
        RoutineCategory health = category(2L, "건강");
        MemberRoutine first = routine(10L, member, exercise, "저녁 산책", DayOfWeek.MONDAY);
        MemberRoutine second = routine(20L, member, health, "영양제 먹기", DayOfWeek.FRIDAY);
        when(memberRoutineRepository.findActiveOrderedByMemberId(MEMBER_ID))
                .thenReturn(List.of(first, second));
        when(memberRoutineRepository.findAllWithSchedulesByIdIn(List.of(10L, 20L)))
                .thenReturn(List.of(second, first));
        when(completionSource.findCompletedRoutineIds(
                List.of(10L, 20L), LocalDate.now(TimeUtil.KST)))
                .thenReturn(Set.of(20L));

        RoutineResDTO.RoutineList result = routineQueryService.getRoutines(MEMBER_ID);

        assertThat(result.routines())
                .extracting(RoutineResDTO.Routine::routineId)
                .containsExactly(10L, 20L);
        assertThat(result.routines().getFirst().repeatDays())
                .containsExactly(DayOfWeek.MONDAY);
        assertThat(result.routines())
                .extracting(RoutineResDTO.Routine::completedToday)
                .containsExactly(false, true);
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verify(memberRoutineRepository).findAllWithSchedulesByIdIn(List.of(10L, 20L));
    }

    @Test
    @DisplayName("활성 개인 루틴이 없으면 일정 조회를 생략하고 빈 목록을 반환한다")
    void getRoutines_NoActiveRoutine_ReturnsEmptyWithoutScheduleQuery() {
        when(memberRoutineRepository.findActiveOrderedByMemberId(MEMBER_ID))
                .thenReturn(List.of());

        RoutineResDTO.RoutineList result = routineQueryService.getRoutines(MEMBER_ID);

        assertThat(result.routines()).isEmpty();
        verify(memberRoutineRepository, never()).findAllWithSchedulesByIdIn(anyList());
        verify(completionSource, never()).findCompletedRoutineIds(anyList(), any());
    }

    private RoutineCategory category(Long id, String name) {
        RoutineCategory category = RoutineCategory.builder().name(name).active(true).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private MemberRoutine routine(
            Long id,
            Member member,
            RoutineCategory category,
            String name,
            DayOfWeek repeatDay
    ) {
        MemberRoutine routine = MemberRoutine.builder()
                .member(member)
                .category(category)
                .name(name)
                .build();
        ReflectionTestUtils.setField(routine, "id", id);
        routine.addSchedule(repeatDay);
        return routine;
    }
}
