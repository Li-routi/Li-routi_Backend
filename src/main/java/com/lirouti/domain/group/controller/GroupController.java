package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.controller.docs.GroupControllerDocs;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.code.success.GroupSuccessCode;
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.domain.group.service.command.GroupInviteCodeCommandService;
import com.lirouti.domain.group.service.query.GroupInviteCodeQueryService;
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
    private final GroupInviteCodeCommandService groupInviteCodeCommandService;
    private final GroupInviteCodeQueryService groupInviteCodeQueryService;

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
    public ApiResponse<GroupResDTO.RoutineCreateResult> createRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.CreateRoutine request
    ) {
        GroupResDTO.RoutineCreateResult result = groupCommandService.createRoutine(
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

    // 그룹 초대 코드 발급&재발급 API
    @Override
    @PostMapping("/{groupId}/invite-code")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResDTO.InviteCode> issueInviteCode(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId
    ) {
        GroupResDTO.InviteCode result = groupInviteCodeCommandService.issueInviteCode(
                groupId,
                userDetails.getMemberId()
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_INVITE_CODE_ISSUE_SUCCESS, result);
    }
}
