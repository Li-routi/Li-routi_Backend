package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.GroupRoutineCategoryRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationReadRepository;
import com.lirouti.global.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    private static final Long MEMBER_ID = 2L;
    private static final Long TARGET_MEMBER_ID = 3L;

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupRoutineCategoryRepository groupRoutineCategoryRepository;
    @Mock
    private GroupRoutineRepository groupRoutineRepository;
    @Mock
    private GroupRoutineAssignmentCommandService assignmentCommandService;
    @Mock
    private GroupRoutineVerificationReadRepository groupRoutineVerificationReadRepository;
    @Mock
    private Group group;
    @Mock
    private GroupRoutineCategory category;
    @Mock
    private GroupMember ownerMembership;
    @Mock
    private GroupMember targetMembership;
    @Mock
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @InjectMocks
    private GroupCommandService groupCommandService;

    @Test
    @DisplayName("잠긴 ACTIVE OWNER 그룹을 애그리거트 루트에서 삭제한다")
    void deleteGroup_ActiveOwner_DeletesGroupRoot() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.ACTIVE);
        when(groupValidationService.validateGroupOwner(group, OWNER_ID))
                .thenReturn(ownerMembership);

        // when
        groupCommandService.deleteGroup(GROUP_ID, OWNER_ID);

        // then
        verify(groupRepository).findByIdForUpdate(GROUP_ID);
        verify(groupValidationService).validateGroupOwner(group, OWNER_ID);
        verify(groupRoutineVerificationReadRepository).deleteAllByGroupId(GROUP_ID);
        verify(groupRepository).delete(group);
    }

    @Test
    @DisplayName("삭제 대상 그룹이 없으면 GROUP_NOT_FOUND를 반환하고 삭제하지 않는다")
    void deleteGroup_GroupNotFound_ThrowsNotFound() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> groupCommandService.deleteGroup(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
        verify(groupRepository, never()).delete(any(Group.class));
        verifyNoInteractions(groupValidationService);
    }

    @Test
    @DisplayName("DELETED 그룹은 삭제 API에서 GROUP_NOT_FOUND로 처리한다")
    void deleteGroup_DeletedGroup_ThrowsNotFound() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.DELETED);

        // when & then
        assertThatThrownBy(() -> groupCommandService.deleteGroup(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
        verifyNoInteractions(groupValidationService);
        verify(groupRepository, never()).delete(any(Group.class));
    }

    @Test
    @DisplayName("OWNER 검증이 실패하면 그룹을 삭제하지 않는다")
    void deleteGroup_NotOwner_DoesNotDeleteGroup() {
        // given
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.ACTIVE);
        when(groupValidationService.validateGroupOwner(group, OWNER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED));

        // when & then
        assertThatThrownBy(() -> groupCommandService.deleteGroup(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        verify(groupRepository, never()).delete(any(Group.class));
    }

    @Test
    @DisplayName("잠긴 ACTIVE OWNER 그룹은 비관적 잠금과 기존 OWNER 검증 후 잠금 처리한다")
    void lockGroup_ActiveOwner_LocksGroup() {
        // given
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(group);
        when(groupValidationService.validateGroupOwner(group, OWNER_ID))
                .thenReturn(ownerMembership);
        when(group.getId()).thenReturn(GROUP_ID);
        when(group.isLocked()).thenReturn(true);

        // when
        GroupResDTO.LockState result = groupCommandService.lockGroup(GROUP_ID, OWNER_ID);

        // then
        assertThat(result.groupId()).isEqualTo(GROUP_ID);
        assertThat(result.isLocked()).isTrue();
        verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        verify(groupValidationService).validateGroupOwner(group, OWNER_ID);
        verify(group).lock();
    }

    @Test
    @DisplayName("잠금 해제는 비관적 잠금과 기존 OWNER 검증 후 잠금 해제 처리한다")
    void unlockGroup_ActiveOwner_UnlocksGroup() {
        // given
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(group);
        when(groupValidationService.validateGroupOwner(group, OWNER_ID))
                .thenReturn(ownerMembership);
        when(group.getId()).thenReturn(GROUP_ID);
        when(group.isLocked()).thenReturn(false);

        // when
        GroupResDTO.LockState result = groupCommandService.unlockGroup(GROUP_ID, OWNER_ID);

        // then
        assertThat(result.groupId()).isEqualTo(GROUP_ID);
        assertThat(result.isLocked()).isFalse();
        verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        verify(groupValidationService).validateGroupOwner(group, OWNER_ID);
        verify(group).unlock();
    }

    @Test
    @DisplayName("존재하지 않는 그룹 잠금 요청은 GROUP_NOT_FOUND를 반환한다")
    void lockGroup_GroupNotFound_ThrowsNotFound() {
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_NOT_FOUND));

        assertThatThrownBy(() -> groupCommandService.lockGroup(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
        verifyNoInteractions(group);
    }

    @Test
    @DisplayName("OWNER 검증에 실패하면 그룹 잠금 상태를 바꾸지 않는다")
    void lockGroup_NotOwner_DoesNotLockGroup() {
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(group);
        when(groupValidationService.validateGroupOwner(group, OWNER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED));

        assertThatThrownBy(() -> groupCommandService.lockGroup(GROUP_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        verify(group, never()).lock();
    }

    private void givenValidatedOwner() {
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(group);
    }

    private void givenActiveCategory() {
        when(groupRoutineCategoryRepository.findByIdAndActiveTrue(3L))
                .thenReturn(Optional.of(category));
        when(category.isUsableBy(GROUP_ID)).thenReturn(true);
    }

    private void givenNoDuplicateTitle() {
        when(groupRoutineRepository.existsByGroupIdAndTitleAndActiveTrue(GROUP_ID, "저녁 루틴"))
                .thenReturn(false);
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
        GroupResDTO.GroupRoutineCreateResult result =
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
        GroupResDTO.GroupRoutineCreateResult result =
                groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request());

        // then
        assertThat(result.assignmentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 카테고리가 없으면 루틴을 저장하지 않는다")
    void createRoutine_CategoryNotFound_ThrowsGroupException() {
        // given
        givenValidatedOwner();
        when(groupRoutineCategoryRepository.findByIdAndActiveTrue(3L))
                .thenReturn(Optional.empty());

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
        when(groupRoutineRepository.existsByGroupIdAndTitleAndActiveTrue(GROUP_ID, "저녁 루틴"))
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
    @DisplayName("그룹에 루틴이 30개 있으면 31번째 루틴을 생성하지 않는다")
    void createRoutine_AtLimit_ThrowsLimitExceeded() {
        // given
        givenValidatedOwner();
        when(groupRoutineRepository.countByGroupIdAndActiveTrue(GROUP_ID))
                .thenReturn((long) GroupRoutine.MAX_GROUP_ROUTINE_COUNT);

        // when & then
        assertThatThrownBy(() -> groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request()))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_LIMIT_EXCEEDED);
        verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        verify(groupRoutineCategoryRepository, never()).findByIdAndActiveTrue(any());
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("다른 그룹의 카테고리로 루틴을 생성할 수 없다")
    void createRoutine_OtherGroupCategory_ThrowsCategoryAccessDenied() {
        // given
        givenValidatedOwner();
        when(groupRoutineCategoryRepository.findByIdAndActiveTrue(3L))
                .thenReturn(Optional.of(category));
        when(category.isUsableBy(GROUP_ID)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request()))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_CATEGORY_ACCESS_DENIED);
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
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
        verify(groupRoutineCategoryRepository, never()).findByIdAndActiveTrue(any());
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
        when(groupRoutineRepository.existsByGroupIdAndTitleAndActiveTrueAndIdNot(
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
        verify(groupRoutineCategoryRepository, never()).findByIdAndActiveTrue(any());
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
        when(groupRoutineRepository.existsByGroupIdAndTitleAndActiveTrueAndIdNot(
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

    @Test
    @DisplayName("다른 그룹의 카테고리로 루틴을 수정할 수 없다")
    void updateRoutine_OtherGroupCategory_ThrowsCategoryAccessDenied() {
        // given
        GroupRoutine routine = existingRoutine();
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupRoutineRepository.findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID))
                .thenReturn(Optional.of(routine));
        when(groupRoutineCategoryRepository.findByIdAndActiveTrue(3L))
                .thenReturn(Optional.of(category));
        when(category.isUsableBy(GROUP_ID)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> groupCommandService.updateRoutine(
                GROUP_ID, ROUTINE_ID, OWNER_ID, updateRequest()
        )).isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_CATEGORY_ACCESS_DENIED);
        verify(groupRoutineRepository, never()).saveAndFlush(any(GroupRoutine.class));
        verify(assignmentCommandService, never())
                .synchronizeRoutineAssignmentsToday(any(GroupRoutine.class));
    }

    @Test
    @DisplayName("활성 구성원이 그룹을 탈퇴하면 멤버십 상태를 변경하고 세션을 회수한다")
    void leaveGroup_ActiveMember_ChangesMembershipAndClosesSessions() {
        // given
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .thenReturn(ownerMembership);

        // when
        groupCommandService.leaveGroup(GROUP_ID, MEMBER_ID);

        // then
        verify(ownerMembership).leave();
        verify(assignmentCommandService)
                .deleteUnfinishedAssignmentsForLeaver(GROUP_ID, MEMBER_ID);
        verify(webSocketSessionRegistry).closeMemberSessions(MEMBER_ID);
        InOrder inOrder = inOrder(groupValidationService);
        inOrder.verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        inOrder.verify(groupValidationService)
                .validateActiveGroupMember(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("OWNER가 활성 구성원을 강제 퇴장시키면 멤버십 상태를 변경하고 대상 세션을 회수한다")
    void kickMember_Owner_KicksTargetAndClosesTargetSessions() {
        // given
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, TARGET_MEMBER_ID))
                .thenReturn(targetMembership);

        // when
        groupCommandService.kickMember(GROUP_ID, OWNER_ID, TARGET_MEMBER_ID);

        // then
        verify(targetMembership).kick();
        verify(webSocketSessionRegistry).closeMemberSessions(TARGET_MEMBER_ID);
        InOrder inOrder = inOrder(groupValidationService);
        inOrder.verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        inOrder.verify(groupValidationService).validateGroupOwner(GROUP_ID, OWNER_ID);
        inOrder.verify(groupValidationService)
                .validateActiveGroupMember(GROUP_ID, TARGET_MEMBER_ID);
    }

    @Test
    @DisplayName("OWNER 권한 검증이 실패하면 대상 구성원을 조회하거나 세션을 회수하지 않는다")
    void kickMember_NonOwner_DoesNotTouchTarget() {
        // given
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED));

        // when & then
        assertThatThrownBy(() -> groupCommandService.kickMember(
                GROUP_ID, OWNER_ID, TARGET_MEMBER_ID
        )).isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        verify(groupValidationService, never())
                .validateActiveGroupMember(GROUP_ID, TARGET_MEMBER_ID);
        verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        verify(webSocketSessionRegistry, never()).closeMemberSessions(any());
    }

    @Test
    @DisplayName("OWNER가 루틴을 삭제하면 미확정 할당 삭제 후 루틴을 비활성화한다")
    void deleteRoutine_Owner_DeletesAssignmentsAndDeactivatesRoutine() {
        // given
        GroupRoutine routine = existingRoutine();
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupRoutineRepository.findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID))
                .thenReturn(Optional.of(routine));
        when(assignmentCommandService.deleteMutableAssignments(ROUTINE_ID)).thenReturn(3);

        // when
        groupCommandService.deleteRoutine(GROUP_ID, ROUTINE_ID, OWNER_ID);

        // then
        assertThat(routine.getActive()).isFalse();
        InOrder inOrder = inOrder(groupValidationService, groupRoutineRepository,
                assignmentCommandService);
        inOrder.verify(groupValidationService).validateGroupOwner(GROUP_ID, OWNER_ID);
        inOrder.verify(groupRoutineRepository).findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID);
        inOrder.verify(assignmentCommandService).deleteMutableAssignments(ROUTINE_ID);
    }

    @Test
    @DisplayName("미확정 할당 삭제가 실패하면 루틴을 비활성화하지 않는다")
    void deleteRoutine_AssignmentDeletionFails_DoesNotDeactivateRoutine() {
        // given
        GroupRoutine routine = mock(GroupRoutine.class);
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupRoutineRepository.findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID))
                .thenReturn(Optional.of(routine));
        when(assignmentCommandService.deleteMutableAssignments(ROUTINE_ID))
                .thenThrow(new IllegalStateException("assignment deletion failure"));

        // when & then
        assertThatThrownBy(() -> groupCommandService
                .deleteRoutine(GROUP_ID, ROUTINE_ID, OWNER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("assignment deletion failure");
        verify(routine, never()).delete();
    }

    @Test
    @DisplayName("요청 그룹의 활성 루틴이 아니면 찾을 수 없는 루틴으로 처리한다")
    void deleteRoutine_RoutineNotFound_ThrowsGroupException() {
        // given
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupRoutineRepository.findByIdAndGroupIdForUpdate(ROUTINE_ID, GROUP_ID))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> groupCommandService
                .deleteRoutine(GROUP_ID, ROUTINE_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_NOT_FOUND);
        verifyNoInteractions(assignmentCommandService);
    }

    @Test
    @DisplayName("OWNER 검증이 실패하면 삭제 대상 루틴을 조회하지 않는다")
    void deleteRoutine_OwnerValidationFails_DoesNotContinue() {
        // given
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED));

        // when & then
        assertThatThrownBy(() -> groupCommandService
                .deleteRoutine(GROUP_ID, ROUTINE_ID, OWNER_ID))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_OWNER_ACCESS_DENIED);
        verifyNoInteractions(groupRoutineRepository, assignmentCommandService);
    }

    private GroupReqDTO.GroupRoutineCreateRequest request() {
        return new GroupReqDTO.GroupRoutineCreateRequest(
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
