package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.code.success.GroupSuccessCode;
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.domain.group.service.command.GroupJoinCommandService;
import com.lirouti.domain.group.service.query.GroupInviteCodeQueryService;
import com.lirouti.domain.group.service.query.GroupJoinQueryService;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupController 단위 테스트")
class GroupControllerUnitTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Long ROUTINE_ID = 20L;

    @Mock
    private GroupCommandService groupCommandService;
    @Mock
    private GroupQueryService groupQueryService;
    @Mock
    private GroupInviteCodeQueryService groupInviteCodeQueryService;
    @Mock
    private GroupJoinQueryService groupJoinQueryService;
    @Mock
    private GroupJoinCommandService groupJoinCommandService;
    @InjectMocks
    private GroupController groupController;

    @Test
    @DisplayName("인증 회원 ID와 요청을 통합 생성 서비스에 전달하고 생성 응답을 반환한다")
    void createGroup_AuthenticatedMember_ReturnsCreateResult() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupReqDTO.CreateGroup request = new GroupReqDTO.CreateGroup(
                "아침 모임", List.of(), List.of()
        );
        GroupResDTO.CreateResult result = GroupResDTO.CreateResult.builder()
                .groupId(10L)
                .name("아침 모임")
                .customCategories(List.of())
                .routines(List.of())
                .assignmentCount(0)
                .build();
        when(groupCommandService.createGroup(MEMBER_ID, request)).thenReturn(result);

        // when
        ApiResponse<GroupResDTO.CreateResult> response =
                groupController.createGroup(principal, request);

        // then
        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_CREATE_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupCommandService).createGroup(MEMBER_ID, request);
    }

    @Test
    @DisplayName("그룹 카테고리 조회에 인증 회원 ID와 그룹 ID를 전달한다")
    void getCategories_AuthenticatedMember_ReturnsCategoryList() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupResDTO.CategoryList result = GroupResDTO.CategoryList.builder()
                .categories(List.of())
                .addableCount(5)
                .build();
        when(groupQueryService.getCategories(GROUP_ID, MEMBER_ID)).thenReturn(result);

        // when
        ApiResponse<GroupResDTO.CategoryList> response =
                groupController.getCategories(principal, GROUP_ID);

        // then
        assertThat(response.getCode()).isEqualTo(
                GroupSuccessCode.GROUP_ROUTINE_CATEGORY_LIST_FETCH_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupQueryService).getCategories(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("인증 회원 ID와 그룹 ID를 그룹 상세 조회 서비스에 전달한다")
    void getGroupDetail_AuthenticatedMember_ReturnsDetail() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupResDTO.Detail result = GroupResDTO.Detail.builder()
                .groupId(GROUP_ID)
                .groupName("아침 모임")
                .inviteCode("DETAIL1")
                .members(List.of())
                .build();
        when(groupQueryService.getGroupDetail(GROUP_ID, MEMBER_ID)).thenReturn(result);

        // when
        ApiResponse<GroupResDTO.Detail> response = groupController.getGroupDetail(principal, GROUP_ID);

        // then
        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_DETAIL_FETCH_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupQueryService).getGroupDetail(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("인증 회원 ID와 상태 메시지 요청을 수정 서비스에 전달한다")
    void updateMyStatusMessage_AuthenticatedMember_ReturnsUpdatedMessage() {
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupReqDTO.UpdateMyStatusMessage request =
                new GroupReqDTO.UpdateMyStatusMessage("오늘도 루틴 완료!");
        GroupResDTO.StatusMessageUpdate result =
                new GroupResDTO.StatusMessageUpdate(GROUP_ID, "오늘도 루틴 완료!");
        when(groupCommandService.updateMyStatusMessage(GROUP_ID, MEMBER_ID, request))
                .thenReturn(result);

        ApiResponse<GroupResDTO.StatusMessageUpdate> response =
                groupController.updateMyStatusMessage(principal, GROUP_ID, request);

        assertThat(response.getCode()).isEqualTo(
                GroupSuccessCode.GROUP_MEMBER_STATUS_MESSAGE_UPDATE_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupCommandService).updateMyStatusMessage(GROUP_ID, MEMBER_ID, request);
    }

    @Test
    @DisplayName("그룹 카테고리 생성에 인증 회원 ID와 그룹 ID 및 요청을 전달한다")
    void createCategory_AuthenticatedOwner_ReturnsCategory() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupReqDTO.CreateCategory request =
                new GroupReqDTO.CreateCategory("아침 관리", RoutineCategoryColor.BLUE);
        GroupResDTO.Category result = GroupResDTO.Category.builder()
                .categoryId(30L).name("아침 관리")
                .color(RoutineCategoryColor.BLUE).fixed(false).build();
        when(groupCommandService.createCategory(GROUP_ID, MEMBER_ID, request)).thenReturn(result);

        // when
        ApiResponse<GroupResDTO.Category> response =
                groupController.createCategory(principal, GROUP_ID, request);

        // then
        assertThat(response.getCode()).isEqualTo(
                GroupSuccessCode.GROUP_ROUTINE_CATEGORY_CREATE_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupCommandService).createCategory(GROUP_ID, MEMBER_ID, request);
    }

    @Test
    @DisplayName("삭제 요청의 인증 회원 ID와 그룹·루틴 ID를 명령 서비스에 전달한다")
    void deleteRoutine_AuthenticatedOwner_ReturnsSuccess() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);

        // when
        ApiResponse<Void> response = groupController
                .deleteRoutine(principal, GROUP_ID, ROUTINE_ID);

        // then
        assertThat(response.getCode()).isEqualTo(
                GroupSuccessCode.GROUP_ROUTINE_DELETE_SUCCESS.getCode());
        assertThat(response.getResult()).isNull();
        verify(groupCommandService).deleteRoutine(GROUP_ID, ROUTINE_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("인증 회원 ID와 그룹 ID를 그룹 삭제 서비스에 전달하고 성공 코드를 반환한다")
    void deleteGroup_AuthenticatedMember_DelegatesAndReturnsSuccess() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);

        // when
        ApiResponse<Void> response = groupController.deleteGroup(principal, GROUP_ID);

        // then
        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_DELETE_SUCCESS.getCode());
        assertThat(response.getResult()).isNull();
        verify(groupCommandService).deleteGroup(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("인증 회원 ID와 그룹 ID를 그룹 탈퇴 서비스에 전달하고 성공 코드를 반환한다")
    void leaveGroup_AuthenticatedMember_DelegatesAndReturnsSuccess() {
        // given
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);

        // when
        ApiResponse<Void> response = groupController.leaveGroup(principal, GROUP_ID);

        // then
        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_LEAVE_SUCCESS.getCode());
        assertThat(response.getResult()).isNull();
        verify(groupCommandService).leaveGroup(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("인증 OWNER의 그룹 잠금 요청을 서비스에 전달하고 잠금 상태를 반환한다")
    void lockGroup_AuthenticatedOwner_ReturnsLockState() {
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupResDTO.LockState result = new GroupResDTO.LockState(GROUP_ID, true);
        when(groupCommandService.lockGroup(GROUP_ID, MEMBER_ID)).thenReturn(result);

        ApiResponse<GroupResDTO.LockState> response = groupController.lockGroup(principal, GROUP_ID);

        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_LOCK_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupCommandService).lockGroup(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("인증 OWNER의 그룹 잠금 해제 요청을 서비스에 전달하고 잠금 상태를 반환한다")
    void unlockGroup_AuthenticatedOwner_ReturnsLockState() {
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupResDTO.LockState result = new GroupResDTO.LockState(GROUP_ID, false);
        when(groupCommandService.unlockGroup(GROUP_ID, MEMBER_ID)).thenReturn(result);

        ApiResponse<GroupResDTO.LockState> response = groupController.unlockGroup(principal, GROUP_ID);

        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_UNLOCK_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupCommandService).unlockGroup(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("참여 Preview 조회에 인증 회원 ID와 초대코드를 전달한다")
    void getJoinPreview_AuthenticatedMember_ReturnsPreview() {
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupResDTO.JoinPreview result = new GroupResDTO.JoinPreview(
                GROUP_ID, "아침 모임", 3, 6, 2, List.of(), true, null);
        when(groupJoinQueryService.getJoinPreview(MEMBER_ID, "AB12CD3")).thenReturn(result);

        ApiResponse<GroupResDTO.JoinPreview> response = groupController
                .getJoinPreview(principal, "AB12CD3");

        assertThat(response.getCode()).isEqualTo(
                GroupSuccessCode.GROUP_JOIN_PREVIEW_FETCH_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupJoinQueryService).getJoinPreview(MEMBER_ID, "AB12CD3");
    }

    @Test
    @DisplayName("인증 회원 ID와 초대코드를 가입 Command에 전달하고 가입 결과를 반환한다")
    void joinGroup_AuthenticatedMember_ReturnsJoinResult() {
        CustomUserDetails principal = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        GroupReqDTO.JoinGroup request = new GroupReqDTO.JoinGroup("AB12CD3");
        GroupResDTO.JoinResult result = new GroupResDTO.JoinResult(
                GROUP_ID, "아침 모임", GroupMemberStatus.ACTIVE);
        when(groupJoinCommandService.join(MEMBER_ID, request)).thenReturn(result);

        ApiResponse<GroupResDTO.JoinResult> response = groupController.joinGroup(principal, request);

        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(GroupSuccessCode.GROUP_JOIN_SUCCESS.getCode());
        assertThat(response.getResult()).isSameAs(result);
        verify(groupJoinCommandService).join(MEMBER_ID, request);
    }
}
