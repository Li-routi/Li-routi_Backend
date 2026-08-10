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

    @Operation(
            summary = "그룹 구성원 찌르기",
            description = """
                    ACTIVE 그룹 구성원이 같은 그룹의 다른 ACTIVE 구성원을 찌릅니다.
                    성공할 때마다 대상의 누적 찔림 수가 1 증가하며, 횟수 제한과 찌르기 이력은 없습니다.
                    커밋 후 대상에게 `GROUP_MEMBER_POKED` 알림 처리를 비동기로 요청하며,
                    알림 처리 실패는 찌르기 성공 결과에 영향을 주지 않습니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `GROUP400_2` | 400 | 자기 자신을 대상으로 요청함 |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_2` | 403 | 요청자가 ACTIVE 그룹 구성원이 아님 |
                    | `GROUP404_1` | 404 | 그룹을 찾을 수 없음 |
                    | `GROUP404_5` | 404 | 대상이 ACTIVE 그룹 구성원이 아님 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 구성원 찌르기 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "자기 자신을 대상으로 요청함"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹 또는 ACTIVE 그룹 구성원이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 ACTIVE 대상 구성원을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.PokeResult> pokeMember(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", example = "1") Long groupId,
            @Parameter(description = "찌를 대상 회원 ID", example = "2") Long targetMemberId
    );

    @Operation(
            summary = "그룹방 상세 조회",
            description = """
                    ACTIVE 그룹 구성원만 그룹명, 초대코드와 ACTIVE 구성원별 활동 현황을 조회할 수 있습니다.
                    금일 진행도는 완료한 그룹 루틴 할당 수와 전체 할당 수이며, 할당이 없는 구성원은 0/0입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹방 상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹 또는 ACTIVE 구성원이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 또는 그룹을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.Detail> getGroupDetail(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", example = "1") Long groupId
    );

    @Operation(
            summary = "내 그룹별 상태 메시지 수정",
            description = """
                    ACTIVE OWNER 또는 MEMBER가 인증 회원 기준으로 자신의 그룹별 상태 메시지를 수정합니다.
                    메시지는 앞뒤 공백을 제거한 뒤 1~255자여야 합니다. 그룹 잠금은 신규 가입만 제어하므로
                    이 수정 권한에는 영향을 주지 않습니다. 다른 그룹의 상태 메시지는 변경하지 않습니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 DTO 검증 실패 |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_2` | 403 | ACTIVE 그룹 구성원이 아님 |
                    | `GROUP404_1` | 404 | 그룹을 찾을 수 없음 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹별 상태 메시지 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "상태 메시지 요청 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹 또는 ACTIVE 구성원이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.StatusMessageUpdate> updateMyStatusMessage(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true, example = "1") Long groupId,
            GroupReqDTO.UpdateMyStatusMessage request
    );

    @Operation(
            summary = "그룹 루틴 카테고리 목록 조회",
            description = """
                    ACTIVE 그룹 구성원만 조회할 수 있습니다.
                    기본 카테고리 6개와 해당 그룹의 활성 사용자 카테고리를 기존 노출 순서로 반환합니다.
                    다른 그룹 및 비활성 카테고리는 포함하지 않습니다.
                    addableCount는 그룹이 더 추가할 수 있는 사용자 카테고리 수입니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_2` | 403 | ACTIVE 그룹 구성원이 아님 |
                    | `GROUP404_1` | 404 | 그룹을 찾을 수 없음 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 루틴 카테고리 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미인증, 비활성 그룹 또는 ACTIVE 구성원이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 또는 그룹을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.CategoryList> getCategories(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", example = "1") Long groupId
    );

    @Operation(
            summary = "그룹 루틴 카테고리 추가",
            description = """
                    ACTIVE OWNER만 그룹 전용 사용자 카테고리를 추가할 수 있습니다.
                    그룹당 활성 사용자 카테고리는 최대 5개입니다. 이름은 앞뒤 공백 제거 후
                    1~10자의 한 줄이어야 하며, 기본 카테고리 및 같은 그룹이 사용한 카테고리
                    이름과 중복될 수 없습니다. 비활성 카테고리 이름도 재사용할 수 없으며,
                    color는 선택값입니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 DTO 검증 실패 |
                    | `GROUP400_1` | 400 | 서비스 경계의 이름 검증 실패 |
                    | `GROUP403_3` | 403 | ACTIVE OWNER가 아님 |
                    | `GROUP409_9` | 409 | 활성 사용자 카테고리 5개 상한 |
                    | `GROUP409_10` | 409 | 기본 또는 같은 그룹 카테고리 이름 중복 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "그룹 루틴 카테고리 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 또는 이름 규칙 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미인증, 비활성 그룹 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 또는 그룹을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "카테고리 상한 또는 이름 중복")
    })
    ApiResponse<GroupResDTO.Category> createCategory(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", example = "1") Long groupId,
            GroupReqDTO.CreateCategory request
    );

    /**
     * 인증 회원을 OWNER로 하는 모임방과 초기 루틴을 통합 생성하는 API 명세다.
     */
    @Operation(
            summary = "모임방 통합 생성",
            description = """
                    인증 회원을 ACTIVE OWNER로 등록하고 그룹 사용자 카테고리, 초기 그룹 루틴,
                    반복 일정 및 생성 당일 OWNER 할당을 하나의 트랜잭션으로 저장합니다.
                    초대코드와 만료 시각은 함께 저장하지만 생성 응답에는 포함하지 않습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "모임방 통합 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 값, 카테고리 참조 또는 일정 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미인증 또는 비활성 회원"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 또는 기본 그룹 카테고리를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "활성 그룹 참여 상한 또는 카테고리 이름 충돌"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "초대코드 unique 충돌 재시도 소진 또는 저장 실패")
    })
    ApiResponse<GroupResDTO.CreateResult> createGroup(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            GroupReqDTO.CreateGroup request
    );

    @Operation(
            summary = "그룹 삭제",
            description = """
                    해당 그룹의 ACTIVE OWNER만 그룹을 삭제할 수 있습니다.
                    그룹과 그룹에 종속된 데이터는 Hard Delete되며, 회원 계정과 다른 그룹의 데이터는 삭제되지 않습니다.
                    공용 기본 그룹 카테고리와 개인 루틴·개인 카테고리도 삭제 대상에 포함되지 않습니다.

                    DELETED 상태이거나 존재하지 않는 그룹은 `GROUP404_1`로 응답합니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `GROUP403_2` | 403 | ACTIVE 그룹 구성원이 아님 (비구성원, LEFT/KICKED 구성원 포함) |
                    | `GROUP403_3` | 403 | ACTIVE OWNER가 아님 |
                    | `GROUP404_1` | 404 | 존재하지 않거나 DELETED 상태인 그룹 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "ACTIVE 그룹 구성원이 아니거나 ACTIVE OWNER 권한이 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "존재하지 않거나 DELETED 상태인 그룹")
    })
    ApiResponse<Void> deleteGroup(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "삭제할 그룹 ID", required = true, example = "1") Long groupId
    );

    @Operation(
            summary = "그룹 방 나가기",
            description = """
                    인증 회원이 자신이 ACTIVE MEMBER로 참여 중인 그룹에서 나갑니다.
                    memberId는 인증 객체에서만 사용하며 요청으로 받지 않습니다. GroupMember 행은 삭제하지 않고
                    LEFT 상태와 탈퇴 시각을 기록합니다.

                    PENDING, IN_PROGRESS 그룹 루틴 할당만 Hard Delete하며, COMPLETED, MISSED 할당과
                    그룹 루틴 인증 이력은 보존합니다. OWNER는 권한을 위임하거나 그룹을 삭제하기 전까지
                    나갈 수 없습니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `GROUP403_1` | 403 | 사용할 수 없는 그룹 |
                    | `GROUP403_2` | 403 | ACTIVE 그룹 구성원이 아님 (비구성원, LEFT/KICKED 포함) |
                    | `GROUP404_1` | 404 | 그룹을 찾을 수 없음 |
                    | `GROUP409_1` | 409 | OWNER는 그룹을 나갈 수 없음 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미인증, 사용할 수 없는 그룹 또는 ACTIVE 구성원이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "OWNER는 그룹을 나갈 수 없음")
    })
    ApiResponse<Void> leaveGroup(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "나갈 그룹 ID", required = true, example = "1") Long groupId
    );

    @Operation(
            summary = "그룹 방 잠금",
            description = """
                    해당 그룹의 ACTIVE OWNER만 신규 참여를 차단하도록 방을 잠글 수 있습니다.
                    그룹 행을 비관적으로 잠근 뒤 권한과 상태를 검증하며, 이미 잠긴 방을 다시 잠그는 요청도
                    현재 잠금 상태를 성공으로 반환하는 멱등 요청입니다. 잠금 과정에서 영구 초대코드는 변경되지 않습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 방 잠금 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹, 비구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.LockState> lockGroup(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "잠글 그룹 ID", required = true, example = "1") Long groupId
    );

    @Operation(
            summary = "그룹 방 잠금 해제",
            description = """
                    해당 그룹의 ACTIVE OWNER만 방 잠금을 해제할 수 있습니다.
                    이미 잠금 해제된 방을 다시 해제하는 요청도 현재 잠금 해제 상태를 성공으로 반환하는 멱등 요청입니다.
                    잠금 해제 과정에서 영구 초대코드는 변경되지 않습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 방 잠금 해제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹, 비구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.LockState> unlockGroup(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "잠금 해제할 그룹 ID", required = true, example = "1") Long groupId
    );

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

    @Operation(
            summary = "초대코드 기반 그룹 참여 Preview 조회",
            description = """
                    인증 회원이 입력한 초대코드로 그룹명, 현재 ACTIVE 인원 및 참여 가능 여부를 조회합니다.
                    Preview는 안내용 읽기 전용 스냅샷으로 가입 관계나 그룹 루틴 할당을 생성하지 않으며,
                    실제 가입 API는 잠금 후 모든 조건을 다시 검증합니다.

                    잠긴 그룹과 비활성 그룹은 조회할 수 없습니다. ACTIVE 구성원, KICKED 구성원,
                    회원의 ACTIVE 그룹 6개 상한, 그룹 ACTIVE 구성원 6명 상한은 200 응답의
                    `joinable=false` 및 `unavailableReason`으로 반환합니다. LEFT 구성원은 재가입 가능으로 판단합니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_5` | 403 | 잠긴 그룹 |
                    | `GROUP404_1` | 404 | 초대코드에 해당하는 그룹 없음 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 참여 Preview 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 회원, 비활성 그룹 또는 잠긴 그룹"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 또는 초대코드에 해당하는 그룹을 찾을 수 없음")
    })
    ApiResponse<GroupResDTO.JoinPreview> getJoinPreview(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 영구 초대코드", required = true, example = "AB12CD3") String inviteCode
    );

    @Operation(
            summary = "초대코드 기반 그룹 가입",
            description = """
                    초대코드로 그룹을 식별한 뒤 그룹 행과 회원 행을 그룹→회원 순서로 비관적 잠금합니다.
                    Preview 결과를 신뢰하지 않고 ACTIVE 상태, 방 잠금, 기존 가입 관계 및 두 참여 상한을
                    모두 다시 검증합니다. 신규 가입과 LEFT 재가입은 가입 당일 아직 종료되지 않은 ACTIVE
                    반복 루틴을 같은 트랜잭션에서 멱등하게 할당하며, 할당 실패 시 가입도 롤백됩니다.
                    성공 시 가입한 그룹의 식별자·이름과 회원 상태(`ACTIVE`)를 반환합니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_5` | 403 | 잠긴 그룹 |
                    | `GROUP404_1` | 404 | 초대코드에 해당하는 그룹 없음 |
                    | `GROUP409_6` | 409 | 회원 ACTIVE 그룹 6개 상한 |
                    | `GROUP409_8` | 409 | 그룹 ACTIVE 구성원 6명 상한 |
                    | `GROUP409_11` | 409 | 이미 ACTIVE 구성원 |
                    | `GROUP409_12` | 409 | KICKED 회원 재가입 불가 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "그룹 가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "초대코드 요청 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 회원, 비활성 그룹 또는 잠긴 그룹"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 또는 초대코드에 해당하는 그룹을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "가입 관계 또는 참여 인원 상한 충돌")
    })
    ApiResponse<GroupResDTO.JoinResult> joinGroup(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            GroupReqDTO.JoinGroup request
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
    ApiResponse<GroupResDTO.GroupRoutineCreateResult> createRoutine(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            GroupReqDTO.GroupRoutineCreateRequest request
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
            summary = "그룹 구성원 강제 퇴장",
            description = "ACTIVE OWNER가 대상 ACTIVE 구성원을 그룹에서 강제 퇴장시킵니다. OWNER는 강제 퇴장시킬 수 없습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 구성원 강제 퇴장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미인증, 비활성 그룹·구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "OWNER는 강제 퇴장시킬 수 없음")
    })
    ApiResponse<Void> kickMember(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(description = "강제 퇴장할 회원 ID", required = true) Long targetMemberId
    );

    @Operation(
            summary = "그룹 루틴 삭제",
            description = """
                    그룹의 ACTIVE OWNER가 요청 그룹에 속한 활성 그룹 루틴을 삭제합니다.
                    루틴은 완료·미이행 할당과 인증 이력을 보존하기 위해 비활성화하고,
                    PENDING·IN_PROGRESS 할당만 물리 삭제합니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_2` | 403 | ACTIVE 그룹 구성원이 아님 |
                    | `GROUP403_3` | 403 | ACTIVE OWNER가 아님 |
                    | `GROUP404_1` | 404 | 그룹을 찾을 수 없음 |
                    | `GROUP404_4` | 404 | 요청 그룹의 활성 루틴을 찾을 수 없음 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 루틴 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 그룹 루틴을 찾을 수 없음")
    })
    ApiResponse<Void> deleteRoutine(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(description = "그룹 루틴 ID", required = true) Long routineId
    );

    @Operation(
            summary = "그룹 초대코드 조회",
            description = "ACTIVE OWNER가 그룹에 영구 귀속된 초대코드를 조회합니다."
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
            summary = "그룹 방 이름 변경",
            description = """
                    ACTIVE OWNER가 그룹 이름을 변경합니다. 이름은 앞뒤 공백을 제거한 뒤 1~20자여야 합니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 DTO 검증 실패 |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_2` | 403 | ACTIVE 그룹 구성원이 아님 |
                    | `GROUP403_3` | 403 | ACTIVE OWNER가 아님 |
                    | `GROUP404_1` | 404 | 그룹을 찾을 수 없음 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 방 이름 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 DTO 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹, 비활성 구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음")
    })
    ApiResponse<Void> updateGroupName(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            GroupReqDTO.UpdateName request
    );

    @Operation(
            summary = "그룹 방장 위임",
            description = """
                    ACTIVE OWNER가 같은 그룹의 ACTIVE 구성원에게 방장 권한을 위임합니다.
                    동일 그룹의 동시 위임 요청은 그룹 행 잠금으로 직렬화됩니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 DTO 검증 실패 |
                    | `GROUP403_1` | 403 | 비활성 그룹 |
                    | `GROUP403_2` | 403 | ACTIVE 그룹 구성원이 아님 |
                    | `GROUP403_3` | 403 | ACTIVE OWNER가 아님 |
                    | `GROUP404_1` | 404 | 그룹을 찾을 수 없음 |
                    | `GROUP409_13` | 409 | 방장 권한을 본인에게 위임하려고 함 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "그룹 방장 위임 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 DTO 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹, 비활성 구성원 또는 OWNER 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "방장 권한을 본인에게 위임할 수 없음")
    })
    ApiResponse<Void> transferGroupOwner(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            GroupReqDTO.TransferOwner request
    );

}
