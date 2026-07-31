package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupCommandService 그룹 루틴 명령 테스트")
class GroupCommandServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long ROUTINE_ID = 20L;
    private static final Long OWNER_ID = 1L;

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private RoutineCategoryRepository routineCategoryRepository;
    @Mock
    private GroupRoutineRepository groupRoutineRepository;
    @Mock
    private GroupRoutineAssignmentCommandService assignmentCommandService;
    @Mock
    private Group group;
    @Mock
    private RoutineCategory category;
    @Mock
    private GroupMember ownerMembership;

    @InjectMocks
    private GroupCommandService groupCommandService;

    private void givenValidatedOwner() {
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(ownerMembership.getGroup()).thenReturn(group);
    }

    private void givenActiveCategory() {
        when(routineCategoryRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(category));
    }

    private void givenNoDuplicateTitle() {
        when(groupRoutineRepository.existsByGroupIdAndTitle(GROUP_ID, "저녁 루틴")).thenReturn(false);
    }

    private void givenResponseReferences() {
        when(group.getId()).thenReturn(GROUP_ID);
        when(category.getId()).thenReturn(3L);
        when(category.getName()).thenReturn("집안일");
    }

    @Test
    @DisplayName("생성 당일 할당 결과 수를 응답에 반환한다")
    void createRoutine_TodaySchedule_ReturnsAssignmentCount() {
        // given
        givenValidatedOwner();
        givenActiveCategory();
        givenNoDuplicateTitle();
        givenResponseReferences();
        when(assignmentCommandService.assignRoutineToActiveMembersToday(any(GroupRoutine.class)))
                .thenReturn(2);

        // when
        GroupResDTO.RoutineCreateResult result =
                groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request());

        // then
        assertThat(result.assignmentCount()).isEqualTo(2);
        verify(assignmentCommandService).assignRoutineToActiveMembersToday(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("OWNER만 있는 그룹에도 할당 한 건으로 정상 생성한다")
    void createRoutine_OwnerOnly_CreatesOneAssignment() {
        // given
        givenValidatedOwner();
        givenActiveCategory();
        givenNoDuplicateTitle();
        givenResponseReferences();
        when(assignmentCommandService.assignRoutineToActiveMembersToday(any(GroupRoutine.class)))
                .thenReturn(1);

        // when
        GroupResDTO.RoutineCreateResult result =
                groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request());

        // then
        assertThat(result.assignmentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 카테고리가 없으면 루틴을 저장하지 않는다")
    void createRoutine_CategoryNotFound_ThrowsGroupException() {
        // given
        givenValidatedOwner();
        when(routineCategoryRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request()))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("동일 그룹의 같은 제목은 생성할 수 없다")
    void createRoutine_DuplicateTitle_ThrowsGroupException() {
        // given
        givenValidatedOwner();
        givenActiveCategory();
        when(groupRoutineRepository.existsByGroupIdAndTitle(GROUP_ID, "저녁 루틴"))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request()))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        verify(assignmentCommandService, never())
                .assignRoutineToActiveMembersToday(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("방장 검증이 실패하면 후속 조회와 저장을 수행하지 않는다")
    void createRoutine_OwnerValidationFails_DoesNotContinue() {
        // given
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED));

        // when & then
        assertThatThrownBy(() -> groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request()))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        verify(routineCategoryRepository, never()).findByIdAndActiveTrue(any());
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("할당 저장 실패는 호출자에게 전파된다")
    void createRoutine_AssignmentSaveFails_PropagatesException() {
        // given
        givenValidatedOwner();
        givenActiveCategory();
        givenNoDuplicateTitle();
        when(assignmentCommandService.assignRoutineToActiveMembersToday(any(GroupRoutine.class)))
                .thenThrow(new IllegalStateException("assignment failure"));

        // when & then
        assertThatThrownBy(() -> groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("assignment failure");
    }

    @Test
    @DisplayName("OWNER가 루틴과 전체 일정을 수정하고 오늘 할당 수를 반환한다")
    void updateRoutine_Owner_UpdatesRoutineAndReturnsAssignmentCount() {
        // given
        GroupRoutine routine = existingRoutine();
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupRoutineRepository.findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID))
                .thenReturn(Optional.of(routine));
        givenActiveCategory();
        when(groupRoutineRepository.existsByGroupIdAndTitleAndIdNot(
                GROUP_ID, "수정 루틴", ROUTINE_ID
        )).thenReturn(false);
        givenResponseReferences();
        when(assignmentCommandService.synchronizeRoutineAssignmentsToday(routine)).thenReturn(2);

        // when
        GroupResDTO.RoutineUpdateResult result = groupCommandService.updateRoutine(
                GROUP_ID,
                ROUTINE_ID,
                OWNER_ID,
                updateRequest()
        );

        // then
        assertThat(result.assignmentCount()).isEqualTo(2);
        assertThat(result.title()).isEqualTo("수정 루틴");
        assertThat(result.schedules())
                .extracting(GroupResDTO.RoutineSchedule::repeatDay)
                .containsExactly(DayOfWeek.TUESDAY);
        assertThat(routine.getCategory()).isSameAs(category);
        assertThat(routine.getTitle()).isEqualTo("수정 루틴");
        verify(groupValidationService).validateGroupOwner(GROUP_ID, OWNER_ID);
        verify(groupRoutineRepository).saveAndFlush(routine);
        verify(assignmentCommandService).synchronizeRoutineAssignmentsToday(routine);
    }

    @Test
    @DisplayName("대상 그룹에 속한 루틴이 없으면 수정하지 않는다")
    void updateRoutine_RoutineNotFound_ThrowsGroupException() {
        // given
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupRoutineRepository.findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> groupCommandService.updateRoutine(
                GROUP_ID, ROUTINE_ID, OWNER_ID, updateRequest()
        )).isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_NOT_FOUND);
        verify(routineCategoryRepository, never()).findByIdAndActiveTrue(any());
        verify(assignmentCommandService, never())
                .synchronizeRoutineAssignmentsToday(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("다른 루틴과 제목이 중복되면 수정하지 않는다")
    void updateRoutine_DuplicateTitle_ThrowsGroupException() {
        // given
        GroupRoutine routine = existingRoutine();
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupRoutineRepository.findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID))
                .thenReturn(Optional.of(routine));
        givenActiveCategory();
        when(groupRoutineRepository.existsByGroupIdAndTitleAndIdNot(
                GROUP_ID, "수정 루틴", ROUTINE_ID
        )).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> groupCommandService.updateRoutine(
                GROUP_ID, ROUTINE_ID, OWNER_ID, updateRequest()
        )).isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.DUPLICATE_GROUP_ROUTINE_TITLE);
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
        verify(assignmentCommandService, never())
                .synchronizeRoutineAssignmentsToday(any(GroupRoutine.class));
    }

    private GroupReqDTO.CreateRoutine request() {
        return new GroupReqDTO.CreateRoutine(
                3L,
                "저녁 루틴",
                "함께 정리합니다.",
                List.of(new GroupReqDTO.RoutineSchedule(
                        DayOfWeek.MONDAY,
                        LocalTime.of(20, 0),
                        LocalTime.of(21, 0)
                ))
        );
    }

    private GroupReqDTO.UpdateRoutine updateRequest() {
        return new GroupReqDTO.UpdateRoutine(
                3L,
                "수정 루틴",
                "수정된 설명입니다.",
                List.of(new GroupReqDTO.RoutineSchedule(
                        DayOfWeek.TUESDAY,
                        LocalTime.of(18, 0),
                        LocalTime.of(19, 0)
                ))
        );
    }

    private GroupRoutine existingRoutine() {
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title("기존 루틴")
                .description("기존 설명")
                .build();
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        return routine;
    }
}
