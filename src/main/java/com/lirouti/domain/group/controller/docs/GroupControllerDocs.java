package com.lirouti.domain.group.controller.docs;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Group", description = "그룹 및 그룹 루틴 API")
public interface GroupControllerDocs {

    /**
     * ACTIVE OWNER 권한을 검증한 뒤 그룹 루틴을 생성하는 API 명세다.
     *
     * @param userDetails 인증 회원 정보
     * @param groupId 루틴을 생성할 그룹 ID
     * @param request 그룹 루틴 생성 요청
     * @return 생성된 그룹 루틴 정보
     */
    @Operation(
            summary = "그룹 루틴 생성",
            description = """
                    그룹의 ACTIVE OWNER가 요일별 일정과 카테고리를 포함한 공동 루틴을 생성합니다.
                    담당 회원은 요청으로 받지 않으며 생성 시점의 ACTIVE 그룹 구성원 전체에게 할당합니다.
                    루틴, 일정, 할당 관계는 하나의 트랜잭션으로 저장됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "그룹 루틴 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값 또는 일정 형식 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "미인증, 비활성 구성원 또는 OWNER 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "그룹, 회원 또는 활성 카테고리를 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "동일 그룹 내 루틴 제목 중복"
            )
    })
    ApiResponse<GroupResDTO.RoutineCreateResult> createRoutine(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            GroupReqDTO.CreateRoutine request
    );
}
