package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.RoutineCategoryRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupCommandService 그룹 루틴 생성 테스트")
class GroupCommandServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;

    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private RoutineCategoryRepository routineCategoryRepository;
    @Mock
    private GroupRoutineRepository groupRoutineRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    @Mock
    private Group group;
    @Mock
    private RoutineCategory category;
    @Mock
    private GroupMember ownerMembership;
    @Mock
    private GroupMember memberMembership;
    @Mock
    private Member owner;
    @Mock
    private Member member;

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
    @DisplayName("ACTIVE OWNER와 MEMBER 모두에게 할당을 생성한다")
    void createRoutine_ActiveMembers_CreatesAssignmentsForAll() {
        // given
        givenValidatedOwner();
        givenActiveCategory();
        givenNoDuplicateTitle();
        givenResponseReferences();
        when(ownerMembership.getMember()).thenReturn(owner);
        when(memberMembership.getMember()).thenReturn(member);
        when(groupMemberRepository.findAllByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership, memberMembership));

        // when
        GroupResDTO.RoutineCreateResult result =
                groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request());

        // then
        assertThat(result.assignmentCount()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupRoutineAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(groupRoutineAssignmentRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue())
                .extracting(GroupRoutineAssignment::getMember)
                .containsExactlyInAnyOrder(owner, member);
    }

    @Test
    @DisplayName("OWNER만 있는 그룹에도 할당 한 건으로 정상 생성한다")
    void createRoutine_OwnerOnly_CreatesOneAssignment() {
        // given
        givenValidatedOwner();
        givenActiveCategory();
        givenNoDuplicateTitle();
        givenResponseReferences();
        when(ownerMembership.getMember()).thenReturn(owner);
        when(groupMemberRepository.findAllByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership));

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
        verify(groupMemberRepository, never())
                .findAllByGroupIdAndStatus(any(), any());
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
        when(ownerMembership.getMember()).thenReturn(owner);
        when(groupMemberRepository.findAllByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership));
        when(groupRoutineAssignmentRepository.saveAllAndFlush(any()))
                .thenThrow(new IllegalStateException("assignment failure"));

        // when & then
        assertThatThrownBy(() -> groupCommandService.createRoutine(GROUP_ID, OWNER_ID, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("assignment failure");
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
}
