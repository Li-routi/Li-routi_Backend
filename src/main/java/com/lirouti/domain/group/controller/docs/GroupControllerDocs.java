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
     * 로그인 회원에게 오늘 할당된 활성 그룹의 루틴 목록 조회 API 명세다.
     *
     * @param userDetails 인증 회원 정보
     * @return 오늘의 그룹 루틴 할당 목록
     */
    @Operation(
            summary = "오늘의 그룹 루틴 조회",
            description = """
                    로그인 회원에게 오늘 할당된 그룹 루틴을 시작 시각 순으로 조회합니다.
                    인증 객체의 회원 ID를 사용하며, 현재 ACTIVE 상태로 참여 중인 그룹의 할당만 반환합니다.
                    그룹에서 탈퇴하거나 강제 퇴장된 경우 기존 할당은 제외하며, 할당이 없으면 빈 목록을 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "오늘의 그룹 루틴 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 인증 토큰"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "미인증 요청 또는 탈퇴·비활성 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "인증 토큰이 참조하는 회원을 찾을 수 없음"
            )
    })
    ApiResponse<GroupResDTO.TodayRoutineList> getTodayRoutines(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );

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

    /**
     * ACTIVE OWNER 권한과 대상 루틴의 그룹 소속을 검증한 뒤 그룹 루틴을 수정하는 API 명세다.
     *
     * @param userDetails 인증 회원 정보
     * @param groupId 루틴이 속한 그룹 ID
     * @param routineId 수정할 그룹 루틴 ID
     * @param request 그룹 루틴 전체 수정 요청
     * @return 수정된 그룹 루틴과 오늘 할당 동기화 결과
     */
    @Operation(
            summary = "그룹 루틴 수정",
            description = """
                    그룹의 ACTIVE OWNER가 카테고리, 제목, 설명과 요일별 반복 일정을 전체 수정합니다.
                    담당 회원은 요청으로 받지 않으며 변경된 오늘 일정은 ACTIVE 구성원 전체에게 동기화됩니다.
                    오늘의 완료·미이행 할당은 확정 이력으로 보존하고 미확정 할당만 변경합니다.
                    루틴, 일정, 오늘 할당 관계는 하나의 트랜잭션으로 처리됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "그룹 루틴 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값 또는 일정 형식 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 인증 토큰"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "미인증, 비활성 구성원 또는 OWNER 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "그룹, 회원, 그룹 루틴 또는 활성 카테고리를 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "동일 그룹 내 루틴 제목 중복 또는 동시 수정 충돌"
            )
    })
    ApiResponse<GroupResDTO.RoutineUpdateResult> updateRoutine(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(description = "그룹 루틴 ID", required = true) Long routineId,
            GroupReqDTO.UpdateRoutine request
    );

    @Operation(
            summary = "그룹 초대코드 조회",
            description = "ACTIVE OWNER가 현재 초대코드와 말소 시각을 조회합니다. 만료된 코드는 자동으로 재발급하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 초대코드 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미인증, 비활성 구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.InviteCode> getInviteCode(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId
    );

    @Operation(
            summary = "그룹 초대코드 발급",
            description = "ACTIVE OWNER가 초대코드를 최초 발급하거나 새로 발급합니다. 새 코드 발급 시 기존 코드는 사용할 수 없게 됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "그룹 초대코드 발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미인증, 비활성 구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "초대코드 발급 실패")
    })
    ApiResponse<GroupResDTO.InviteCode> issueInviteCode(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId
    );
}
