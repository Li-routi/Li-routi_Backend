package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.controller.docs.GroupControllerDocs;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.code.success.GroupSuccessCode;
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController implements GroupControllerDocs {
    private final GroupCommandService groupCommandService;
    private final GroupQueryService groupQueryService;

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
}
