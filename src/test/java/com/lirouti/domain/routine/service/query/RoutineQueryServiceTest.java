package com.lirouti.domain.routine.service.query;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.routine.cache.RoutineTemplateCacheReader;
import com.lirouti.domain.routine.cache.RoutineTemplateCacheReader.CachedTemplate;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.exception.RoutineException;
import com.lirouti.domain.routine.exception.code.error.RoutineErrorCode;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutineQueryService 테스트")
class RoutineQueryServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private RoutineCategoryRepository routineCategoryRepository;
    @Mock
    private RoutineTemplateCacheReader routineTemplateCacheReader;
    @Mock
    private MemberRoutineRepository memberRoutineRepository;
    @Mock
    private RoutineCompletionSource completionSource;

    @InjectMocks
    private RoutineQueryService routineQueryService;

    @Test
    @DisplayName("전체 기본 루틴은 캐시 순서를 유지하고 회원별 추가 상태를 붙인다")
    void getTemplates_AllTemplates_CombinesCachedDataWithMemberState() {
        when(routineTemplateCacheReader.getAll()).thenReturn(cachedTemplates());
        when(memberRoutineRepository.findTemplateIdsByMemberId(MEMBER_ID))
                .thenReturn(List.of(21L));

        RoutineResDTO.TemplateList result = routineQueryService.getTemplates(MEMBER_ID, null);

        assertThat(result.templates())
                .extracting(
                        RoutineResDTO.Template::templateId,
                        RoutineResDTO.Template::alreadyAdded
                )
                .containsExactly(
                        tuple(11L, false),
                        tuple(21L, true),
                        tuple(22L, false)
                );
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verify(routineTemplateCacheReader).getAll();
        verify(memberRoutineRepository).findTemplateIdsByMemberId(MEMBER_ID);
        verifyNoInteractions(routineCategoryRepository);
    }

    @Test
    @DisplayName("고정 카테고리 권한을 캐시보다 먼저 검증하고 해당 템플릿만 순서대로 반환한다")
    void getTemplates_FixedCategory_ValidatesBeforeCacheAndFiltersInMemory() {
        when(routineCategoryRepository.findByIdAndActiveTrue(2L))
                .thenReturn(Optional.of(category(2L, "건강")));
        when(routineTemplateCacheReader.getAll()).thenReturn(cachedTemplates());
        when(memberRoutineRepository.findTemplateIdsByMemberId(MEMBER_ID))
                .thenReturn(List.of());

        RoutineResDTO.TemplateList result = routineQueryService.getTemplates(MEMBER_ID, 2L);

        assertThat(result.templates())
                .extracting(RoutineResDTO.Template::templateId)
                .containsExactly(21L, 22L);
        InOrder order = inOrder(
                memberQueryService,
                routineCategoryRepository,
                routineTemplateCacheReader,
                memberRoutineRepository
        );
        order.verify(memberQueryService).getActiveMember(MEMBER_ID);
        order.verify(routineCategoryRepository).findByIdAndActiveTrue(2L);
        order.verify(routineTemplateCacheReader).getAll();
        order.verify(memberRoutineRepository).findTemplateIdsByMemberId(MEMBER_ID);
    }

    @Test
    @DisplayName("본인 사용자 카테고리는 접근할 수 있지만 기본 템플릿 목록은 비어 있다")
    void getTemplates_OwnCategory_ReturnsEmptyList() {
        RoutineCategory ownCategory = ownedCategory(7L, "나만의 루틴", MEMBER_ID);
        when(routineCategoryRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(ownCategory));
        when(routineTemplateCacheReader.getAll()).thenReturn(cachedTemplates());
        when(memberRoutineRepository.findTemplateIdsByMemberId(MEMBER_ID))
                .thenReturn(List.of());

        RoutineResDTO.TemplateList result = routineQueryService.getTemplates(MEMBER_ID, 7L);

        assertThat(result.templates()).isEmpty();
        verify(routineTemplateCacheReader).getAll();
        verify(memberRoutineRepository).findTemplateIdsByMemberId(MEMBER_ID);
    }

    @Test
    @DisplayName("다른 회원의 사용자 카테고리는 캐시 조회 전에 거부한다")
    void getTemplates_OtherMemberCategory_DeniesBeforeCache() {
        RoutineCategory otherMemberCategory = ownedCategory(7L, "남의 루틴", OTHER_MEMBER_ID);
        when(routineCategoryRepository.findByIdAndActiveTrue(7L))
                .thenReturn(Optional.of(otherMemberCategory));

        assertThatThrownBy(() -> routineQueryService.getTemplates(MEMBER_ID, 7L))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_CATEGORY_ACCESS_DENIED);
        verifyNoInteractions(routineTemplateCacheReader);
        verify(memberRoutineRepository, never()).findTemplateIdsByMemberId(anyLong());
    }

    @Test
    @DisplayName("없는 카테고리는 캐시 조회 전에 기존 not found 예외를 유지한다")
    void getTemplates_MissingCategory_ThrowsBeforeCache() {
        when(routineCategoryRepository.findByIdAndActiveTrue(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> routineQueryService.getTemplates(MEMBER_ID, 99L))
                .isInstanceOf(RoutineException.class)
                .extracting("code")
                .isEqualTo(RoutineErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
        verifyNoInteractions(routineTemplateCacheReader);
        verify(memberRoutineRepository, never()).findTemplateIdsByMemberId(anyLong());
    }

    @Test
    @DisplayName("같은 공통 캐시를 사용해도 alreadyAdded는 요청 회원마다 다시 조립한다")
    void getTemplates_SameCache_UsesPerMemberAddedState() {
        when(routineTemplateCacheReader.getAll()).thenReturn(cachedTemplates());
        when(memberRoutineRepository.findTemplateIdsByMemberId(MEMBER_ID))
                .thenReturn(List.of(11L));
        when(memberRoutineRepository.findTemplateIdsByMemberId(OTHER_MEMBER_ID))
                .thenReturn(List.of(21L));

        RoutineResDTO.TemplateList first = routineQueryService.getTemplates(MEMBER_ID, null);
        RoutineResDTO.TemplateList second = routineQueryService.getTemplates(OTHER_MEMBER_ID, null);

        assertThat(first.templates())
                .filteredOn(RoutineResDTO.Template::alreadyAdded)
                .extracting(RoutineResDTO.Template::templateId)
                .containsExactly(11L);
        assertThat(second.templates())
                .filteredOn(RoutineResDTO.Template::alreadyAdded)
                .extracting(RoutineResDTO.Template::templateId)
                .containsExactly(21L);
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verify(memberQueryService).getActiveMember(OTHER_MEMBER_ID);
        verify(routineTemplateCacheReader, times(2)).getAll();
    }

    @Test
    @DisplayName("리포지토리 노출 순서를 유지하며 반복 일정을 묶음 조회한다")
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

        RoutineResDTO.RoutineList result = routineQueryService.getRoutines(MEMBER_ID);

        assertThat(result.routines())
                .extracting(RoutineResDTO.Routine::routineId)
                .containsExactly(10L, 20L);
        assertThat(result.routines().getFirst().repeatDays())
                .containsExactly(DayOfWeek.MONDAY);
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
    }

    private RoutineCategory category(Long id, String name) {
        RoutineCategory category = RoutineCategory.builder().name(name).active(true).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private RoutineCategory ownedCategory(Long id, String name, Long ownerId) {
        Member owner = mock(Member.class);
        when(owner.getId()).thenReturn(ownerId);
        RoutineCategory category = RoutineCategory.builder()
                .owner(owner)
                .name(name)
                .active(true)
                .build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private List<CachedTemplate> cachedTemplates() {
        return List.of(
                new CachedTemplate(11L, 1L, "운동", "아침 스트레칭"),
                new CachedTemplate(21L, 2L, "건강", "물 마시기"),
                new CachedTemplate(22L, 2L, "건강", "영양제 먹기")
        );
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
