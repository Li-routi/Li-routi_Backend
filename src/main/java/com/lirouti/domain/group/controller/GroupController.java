package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.controller.docs.GroupControllerDocs;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.code.success.GroupSuccessCode;
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.domain.group.service.command.GroupJoinCommandService;
import com.lirouti.domain.group.service.query.GroupInviteCodeQueryService;
import com.lirouti.domain.group.service.query.GroupJoinQueryService;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController implements GroupControllerDocs {
    private final GroupCommandService groupCommandService;
    private final GroupQueryService groupQueryService;
    private final GroupInviteCodeQueryService groupInviteCodeQueryService;
    private final GroupJoinQueryService groupJoinQueryService;
    private final GroupJoinCommandService groupJoinCommandService;

    /** 모임방과 초기 카테고리·루틴·일정을 한 요청으로 생성한다. */
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResDTO.CreateResult> createGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody GroupReqDTO.CreateGroup request
    ) {
        GroupResDTO.CreateResult result = groupCommandService.createGroup(
                userDetails.getMemberId(),
                request
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_CREATE_SUCCESS, result);
    }

    /** ACTIVE OWNER가 그룹과 그룹에 종속된 데이터를 Hard Delete한다. */
    @Override
    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> deleteGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        groupCommandService.deleteGroup(groupId, userDetails.getMemberId());
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_DELETE_SUCCESS, null);
    }

    /** ACTIVE 일반 구성원이 그룹을 탈퇴하고 미완료 그룹 루틴 할당을 정리한다. */
    @Override
    @DeleteMapping("/{groupId}/leave")
    public ApiResponse<Void> leaveGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        groupCommandService.leaveGroup(groupId, userDetails.getMemberId());
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_LEAVE_SUCCESS, null);
    }

    /** ACTIVE OWNER가 신규 참여를 차단하도록 그룹을 잠근다. */
    @Override
    @PatchMapping("/{groupId}/lock")
    public ApiResponse<GroupResDTO.LockState> lockGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        GroupResDTO.LockState result = groupCommandService.lockGroup(
                groupId, userDetails.getMemberId());
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_LOCK_SUCCESS, result);
    }

    /** ACTIVE OWNER가 그룹 잠금을 해제한다. */
    @Override
    @PatchMapping("/{groupId}/unlock")
    public ApiResponse<GroupResDTO.LockState> unlockGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        GroupResDTO.LockState result = groupCommandService.unlockGroup(
                groupId, userDetails.getMemberId());
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_UNLOCK_SUCCESS, result);
    }

    /** ACTIVE OWNER가 그룹 이름을 변경한다. */
    @Override
    @PatchMapping("/{groupId}/name")
    public ApiResponse<Void> updateGroupName(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.UpdateName request
    ) {
        groupCommandService.updateGroupName(groupId, userDetails.getMemberId(), request);
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_NAME_UPDATE_SUCCESS, null);
    }

    /** ACTIVE OWNER가 같은 그룹의 ACTIVE 구성원에게 방장 권한을 위임한다. */
    @Override
    @PatchMapping("/{groupId}/owner")
    public ApiResponse<Void> transferGroupOwner(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.TransferOwner request
    ) {
        groupCommandService.transferGroupOwner(groupId, userDetails.getMemberId(), request);
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_OWNER_TRANSFER_SUCCESS, null);
    }

    /** ACTIVE 그룹 구성원이 사용할 수 있는 그룹 루틴 카테고리를 조회한다. */
    @Override
    @GetMapping("/{groupId}/categories")
    public ApiResponse<GroupResDTO.CategoryList> getCategories(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        GroupResDTO.CategoryList result = groupQueryService.getCategories(
                groupId,
                userDetails.getMemberId()
        );
        return ApiResponse.onSuccess(
                GroupSuccessCode.GROUP_ROUTINE_CATEGORY_LIST_FETCH_SUCCESS,
                result
        );
    }

    /** ACTIVE 그룹 구성원의 그룹방 진입 화면 정보를 조회한다. */
    @Override
    @GetMapping("/{groupId}")
    public ApiResponse<GroupResDTO.Detail> getGroupDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        GroupResDTO.Detail result = groupQueryService.getGroupDetail(
                groupId,
                userDetails.getMemberId()
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_DETAIL_FETCH_SUCCESS, result);
    }

    /** ACTIVE OWNER가 그룹 전용 사용자 카테고리를 추가한다. */
    @Override
    @PostMapping("/{groupId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResDTO.Category> createCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.CreateCategory request
    ) {
        GroupResDTO.Category result = groupCommandService.createCategory(
                groupId,
                userDetails.getMemberId(),
                request
        );
        return ApiResponse.onSuccess(
                GroupSuccessCode.GROUP_ROUTINE_CATEGORY_CREATE_SUCCESS,
                result
        );
    }

    /**
     * 로그인 회원에게 오늘 할당된 활성 그룹의 루틴을 조회한다.
     *
     * @param userDetails 인증 회원 정보
     * @return 오늘의 그룹 루틴 할당 목록
     */
    @Override
    @GetMapping("/routines/today")
    public ApiResponse<GroupResDTO.TodayRoutineList> getTodayRoutines(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        GroupResDTO.TodayRoutineList result = groupQueryService
                .getTodayRoutines(userDetails.getMemberId());
        return ApiResponse.onSuccess(
                GroupSuccessCode.GROUP_ROUTINE_TODAY_FETCH_SUCCESS,
                result
        );
    }

    /** 초대코드 입력 시 인증 회원이 해당 그룹에 참여할 수 있는지 안내용 정보를 조회한다. */
    @Override
    @GetMapping("/join/preview")
    public ApiResponse<GroupResDTO.JoinPreview> getJoinPreview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String inviteCode
    ) {
        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(
                userDetails.getMemberId(), inviteCode);
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_JOIN_PREVIEW_FETCH_SUCCESS, result);
    }

    /** 초대코드로 그룹 가입과 가입 당일 수행 가능한 루틴 할당을 하나의 트랜잭션으로 처리한다. */
    @Override
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResDTO.JoinResult> joinGroup(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody GroupReqDTO.JoinGroup request
    ) {
        GroupResDTO.JoinResult result = groupJoinCommandService.join(
                userDetails.getMemberId(), request);
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_JOIN_SUCCESS, result);
    }

    /**
     * 인증 회원이 소유한 그룹에 반복 일정이 포함된 공동 루틴을 생성한다.
     *
     * @param userDetails 인증 회원 정보
     * @param groupId 루틴을 생성할 그룹 ID
     * @param request 카테고리, 제목, 설명 및 반복 일정
     * @return 생성된 루틴과 당일 할당 결과
     */
    @Override
    @PostMapping("/{groupId}/routines")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResDTO.GroupRoutineCreateResult> createRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.GroupRoutineCreateRequest request
    ) {
        GroupResDTO.GroupRoutineCreateResult result = groupCommandService.createRoutine(
                groupId,
                userDetails.getMemberId(),
                request
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_ROUTINE_CREATE_SUCCESS, result);
    }

    /**
     * 인증 회원이 소유한 그룹의 공동 루틴과 반복 일정을 전체 수정한다.
     *
     * @param userDetails 인증 회원 정보
     * @param groupId 루틴이 속한 그룹 ID
     * @param routineId 수정할 그룹 루틴 ID
     * @param request 카테고리, 제목, 설명 및 변경 후 전체 반복 일정
     * @return 수정된 루틴과 동기화 후 오늘 할당 결과
     */
    @Override
    @PutMapping("/{groupId}/routines/{routineId}")
    public ApiResponse<GroupResDTO.RoutineUpdateResult> updateRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @PathVariable Long routineId,
            @Valid @RequestBody GroupReqDTO.UpdateRoutine request
    ) {
        GroupResDTO.RoutineUpdateResult result = groupCommandService.updateRoutine(
                groupId,
                routineId,
                userDetails.getMemberId(),
                request
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_ROUTINE_UPDATE_SUCCESS, result);
    }

    /** ACTIVE OWNER가 대상 구성원을 그룹에서 강제 퇴장시킨다. */
    @Override
    @DeleteMapping("/{groupId}/members/{targetMemberId}")
    public ApiResponse<Void> kickMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @PathVariable Long targetMemberId
    ) {
        groupCommandService.kickMember(
                groupId,
                userDetails.getMemberId(),
                targetMemberId
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_MEMBER_KICK_SUCCESS, null);
    }

    /** 인증 회원이 소유한 그룹의 공동 루틴을 삭제한다. */
    @Override
    @DeleteMapping("/{groupId}/routines/{routineId}")
    public ApiResponse<Void> deleteRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @PathVariable Long routineId
    ) {
        groupCommandService.deleteRoutine(groupId, routineId, userDetails.getMemberId());
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_ROUTINE_DELETE_SUCCESS, null);
    }

    // 그룹 초대 코드 조회 API
    @Override
    @GetMapping("/{groupId}/invite-code")
    public ApiResponse<GroupResDTO.InviteCode> getInviteCode(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        GroupResDTO.InviteCode result = groupInviteCodeQueryService.getInviteCode(
                groupId,
                userDetails.getMemberId()
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_INVITE_CODE_FETCH_SUCCESS, result);
    }

}
