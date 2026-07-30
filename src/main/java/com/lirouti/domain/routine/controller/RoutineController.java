package com.lirouti.domain.routine.controller;

import com.lirouti.domain.routine.controller.docs.RoutineControllerDocs;
import com.lirouti.domain.routine.dto.request.RoutineReqDTO;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.exception.code.success.RoutineSuccessCode;
import com.lirouti.domain.routine.service.command.RoutineCommandService;
import com.lirouti.domain.routine.service.query.RoutineQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/routines")
public class RoutineController implements RoutineControllerDocs {
    private final RoutineQueryService routineQueryService;
    private final RoutineCommandService routineCommandService;

    /**
     * 루틴 추가 화면의 카테고리 칩 목록을 조회한다.
     *
     * @param userDetails 인증 회원 정보
     * @return 고정 카테고리와 인증 회원의 사용자 카테고리 목록
     */
    @Override
    @GetMapping("/categories")
    public ApiResponse<RoutineResDTO.CategoryList> getCategories(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        RoutineResDTO.CategoryList result = routineQueryService
                .getCategories(userDetails.getMemberId());
        return ApiResponse.onSuccess(
                RoutineSuccessCode.ROUTINE_CATEGORY_LIST_FETCH_SUCCESS,
                result
        );
    }

    /**
     * 카테고리별 기본 제공 루틴 목록을 조회한다.
     *
     * @param userDetails 인증 회원 정보
     * @param categoryId 조회할 카테고리 ID. 생략하면 전체 카테고리
     * @return 기본 제공 루틴 목록
     */
    @Override
    @GetMapping("/templates")
    public ApiResponse<RoutineResDTO.TemplateList> getTemplates(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long categoryId
    ) {
        RoutineResDTO.TemplateList result = routineQueryService
                .getTemplates(userDetails.getMemberId(), categoryId);
        return ApiResponse.onSuccess(
                RoutineSuccessCode.ROUTINE_TEMPLATE_LIST_FETCH_SUCCESS,
                result
        );
    }

    /**
     * 선택하거나 직접 작성한 개인 루틴을 한 번에 생성한다.
     *
     * @param userDetails 인증 회원 정보
     * @param request 등록할 루틴 목록
     * @return 생성된 루틴과 생성 후 활성 루틴 총 개수
     */
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoutineResDTO.RoutineCreateResult> createRoutines(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RoutineReqDTO.CreateRoutines request
    ) {
        RoutineResDTO.RoutineCreateResult result = routineCommandService
                .createRoutines(userDetails.getMemberId(), request);
        return ApiResponse.onSuccess(RoutineSuccessCode.ROUTINE_CREATE_SUCCESS, result);
    }

    /**
     * 인증 회원이 직접 쓰는 사용자 카테고리를 추가한다.
     *
     * @param userDetails 인증 회원 정보
     * @param request 카테고리 이름과 색상
     * @return 생성된 카테고리
     */
    @Override
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoutineResDTO.Category> createCategory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RoutineReqDTO.CreateCategory request
    ) {
        RoutineResDTO.Category result = routineCommandService
                .createCategory(userDetails.getMemberId(), request);
        return ApiResponse.onSuccess(RoutineSuccessCode.ROUTINE_CATEGORY_CREATE_SUCCESS, result);
    }
}
