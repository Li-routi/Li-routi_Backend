package com.lirouti.domain.routine.controller.docs;

import com.lirouti.domain.routine.dto.request.RoutineReqDTO;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

// 태그 이름에 "개인"을 박아 둔다. Group 태그에도 그룹 루틴 API가 있어서, 문서를 처음 여는
// 사람이 두 태그를 나란히 보면 어느 쪽이 내 루틴인지 이름만으로는 구분되지 않는다.
@Tag(name = "Routine (개인 루틴)", description = "개인 루틴 및 루틴 카테고리 API")
public interface RoutineControllerDocs {

    /**
     * 루틴 추가 화면의 카테고리 칩 목록 조회 API 명세다.
     *
     * @param userDetails 인증 회원 정보
     * @return 고정 카테고리와 인증 회원의 사용자 카테고리 목록
     */
    @Operation(
            summary = "루틴 카테고리 목록 조회",
            description = """
                    앱이 제공하는 고정 카테고리(운동, 건강, 자기계발, 생활정리, 마음관리, 취미)와
                    인증 회원이 추가한 사용자 카테고리를 화면 노출 순서대로 조회합니다.
                    고정 카테고리가 먼저 오고, 사용자 카테고리는 생성 순서로 이어집니다.
                    addableCount는 더 추가할 수 있는 사용자 카테고리 수(최대 5개 기준)입니다.
                    0이면 추가 버튼을 비활성을 부탁 드리겠습니다:)

                    fixed가 true면 앱이 제공하는 고정 카테고리라 수정·삭제할 수 없고,
                    false면 이 회원이 만든 카테고리입니다.
                    사용자 카테고리도 색을 "없음"으로 고를 수 있어 color만으로는 구분할 수 없습니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `MEMBER403_1` | 403 | 탈퇴·비활성 회원 | 로그아웃 처리 |
                    | `MEMBER404_1` | 404 | 토큰이 가리키는 회원이 없음 | 로그아웃 처리 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "루틴 카테고리 목록 조회 성공"
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
    ApiResponse<RoutineResDTO.CategoryList> getCategories(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
            summary = "사용자 루틴 카테고리 수정",
            description = """
                    루틴 추가 화면에서 인증 회원이 직접 만든 사용자 카테고리의 이름과 색상을 수정합니다.
                    이름을 바꾸더라도 해당 카테고리에 속한 개인 루틴은 같은 categoryId를 계속 참조하므로
                    루틴 데이터에는 영향을 주지 않습니다.

                    ### 규칙

                    - 본인이 만든 활성 사용자 카테고리만 수정할 수 있습니다.
                    - 고정 카테고리(운동, 건강, 자기계발, 생활정리, 마음관리, 취미)는 수정할 수 없습니다.
                    - 이름은 앞뒤 공백을 제거한 뒤 1~10자여야 하며 줄바꿈을 포함할 수 없습니다.
                    - 고정 카테고리 및 본인의 다른 카테고리와 같은 이름은 사용할 수 없습니다.
                    - 기존 이름을 그대로 두고 색상만 바꾸는 것은 허용됩니다.
                    - color를 `null`로 보내면 "색 없음"으로 저장됩니다.

                    ### color 값

                    색상 칩은 `RED`, `ORANGE`, `YELLOW`, `GREEN`, `BLUE`, `MAGENTA`, `BLACK`
                    일곱 가지입니다. 실제 색상 값(HEX)은 테마·플랫폼마다 달라 서버가 정하지 않습니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 형식 검증 실패 — 이름이 비었거나 10자 초과·줄바꿈 포함, 없는 color 값 | 해당 입력 필드에 안내 |
                    | `ROUTINE400_2` | 400 | 이름이 앞뒤 공백 제거 후 1~10자를 벗어남 | 이름 입력란에 안내 |
                    | `ROUTINE403_1` | 403 | 다른 회원이 만든 카테고리 | 카테고리 목록을 다시 조회 |
                    | `ROUTINE403_2` | 403 | 고정 카테고리 수정 시도 | 수정 화면을 닫고 카테고리 목록을 다시 조회 |
                    | `MEMBER403_1` | 403 | 탈퇴·비활성 회원 | 로그아웃 처리 |
                    | `MEMBER404_1` | 404 | 토큰이 가리키는 회원이 없음 | 로그아웃 처리 |
                    | `ROUTINE404_1` | 404 | 카테고리가 없거나 비활성 | 카테고리 목록을 다시 조회 |
                    | `ROUTINE409_4` | 409 | 고정 카테고리 또는 본인의 다른 카테고리와 이름 중복 | 이름 입력란에 중복 안내 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "사용자 카테고리 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "이름 규칙 위반"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "고정 또는 다른 회원의 카테고리"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "카테고리를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "카테고리 이름 중복")
    })
    ApiResponse<RoutineResDTO.Category> updateCategory(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "수정할 사용자 카테고리 ID", example = "7") Long categoryId,
            RoutineReqDTO.UpdateCategory request
    );

    @Operation(
            summary = "사용자 루틴 카테고리 삭제",
            description = """
                    인증 회원이 직접 만든 사용자 카테고리를 삭제합니다.
                    삭제 성공 후 `GET /api/routines/categories`를 다시 조회하면 카테고리가 목록에서 사라지고
                    `addableCount`가 1 증가합니다.

                    ### 규칙

                    - 본인이 만든 활성 사용자 카테고리만 삭제할 수 있습니다.
                    - 고정 카테고리(운동, 건강, 자기계발, 생활정리, 마음관리, 취미)는 삭제할 수 없습니다.
                    - 활성·비활성 여부와 관계없이 개인 루틴이 하나라도 포함된 카테고리는 삭제할 수 없습니다.
                    - 루틴이 전혀 없는 카테고리는 물리 삭제합니다.
                    - 물리 삭제이므로 삭제 후 같은 이름의 사용자 카테고리를 다시 만들 수 있습니다.
                    - 성공 응답의 result는 `null`입니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `ROUTINE403_1` | 403 | 다른 회원이 만든 카테고리 | 카테고리 목록을 다시 조회 |
                    | `ROUTINE403_2` | 403 | 고정 카테고리 삭제 시도 | 삭제 화면을 닫고 카테고리 목록을 다시 조회 |
                    | `MEMBER403_1` | 403 | 탈퇴·비활성 회원 | 로그아웃 처리 |
                    | `MEMBER404_1` | 404 | 토큰이 가리키는 회원이 없음 | 로그아웃 처리 |
                    | `ROUTINE404_1` | 404 | 카테고리가 없거나 비활성 | 카테고리 목록을 다시 조회 |
                    | `ROUTINE409_5` | 409 | 활성 또는 비활성 개인 루틴이 하나라도 포함됨 | 루틴을 다른 카테고리로 옮기거나 삭제해야 함을 안내 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "사용자 카테고리 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "고정 또는 다른 회원의 카테고리"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "카테고리를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "개인 루틴이 포함된 카테고리")
    })
    ApiResponse<Void> deleteCategory(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "삭제할 사용자 카테고리 ID", example = "7") Long categoryId
    );

    /**
     * 카테고리별 기본 제공 루틴 목록 조회 API 명세다.
     *
     * @param userDetails 인증 회원 정보
     * @param categoryId 조회할 카테고리 ID. 생략하면 전체 카테고리
     * @return 기본 제공 루틴 목록
     */
    @Operation(
            summary = "기본 제공 루틴 목록 조회",
            description = """
                    카테고리마다 앱이 미리 제공하는 루틴 목록을 조회합니다.
                    categoryId를 생략하면 화면의 `전체` 탭에 해당하는 모든 카테고리의 목록을
                    카테고리 순서대로 반환합니다.
                    사용자 카테고리에는 기본 제공 루틴이 없으므로 빈 목록이 반환될 수 있습니다.
                    alreadyAdded가 true면 인증 회원이 이미 등록한 기본 루틴이며, 다시 등록할 수 없습니다.

                    고정 카테고리 ID는 운동 1, 건강 2, 자기계발 3, 생활정리 4, 마음관리 5, 취미 6입니다.
                    응답의 templateId를 `POST /api/routines`의 templateId에 그대로 넣으면 됩니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `ROUTINE403_1` | 403 | 다른 회원이 만든 카테고리를 조회 | 카테고리 목록을 다시 조회 |
                    | `MEMBER403_1` | 403 | 탈퇴·비활성 회원 | 로그아웃 처리 |
                    | `MEMBER404_1` | 404 | 토큰이 가리키는 회원이 없음 | 로그아웃 처리 |
                    | `ROUTINE404_1` | 404 | categoryId가 없거나 비활성 | 카테고리 목록을 다시 조회 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기본 제공 루틴 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 인증 토큰"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "미인증 요청, 탈퇴·비활성 회원 또는 다른 회원의 카테고리 조회"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 활성 카테고리를 찾을 수 없음"
            )
    })
    ApiResponse<RoutineResDTO.TemplateList> getTemplates(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "조회할 카테고리 ID. 생략하면 전체", example = "2") Long categoryId
    );

    @Operation(
            summary = "개인 루틴 목록 조회",
            description = """
                    인증 회원의 활성 개인 루틴을 조회합니다.
                    카테고리 노출 순서대로 정렬하며, 같은 카테고리에서는 기본 제공 루틴을 먼저,
                    사용자가 직접 추가한 루틴을 생성 순서대로 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "개인 루틴 목록 조회 성공"
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
    ApiResponse<RoutineResDTO.RoutineList> getRoutines(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
            summary = "개인 루틴 수정",
            description = """
                    인증 회원이 소유한 활성 개인 루틴의 설정을 수정합니다.
                    설정 화면의 전체 폼을 받으므로 name, endTime, repeatDays는 필수이고
                    alarmTime은 null이면 알람 없음으로 저장됩니다.
                    기본 제공 루틴의 이름을 바꾸면 templateId 참조가 해제되며,
                    이름을 유지하고 시간·요일·알람만 바꾸면 참조를 유지합니다.
                    카테고리 이동은 지원하지 않습니다.

                    ### 에러 코드
                    | code | HTTP | 언제 |
                    | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 형식 또는 필수값 검증 실패 |
                    | `ROUTINE400_1` | 400 | 이름 규칙 위반 |
                    | `ROUTINE400_4` | 400 | 마감 시각·반복 요일 규칙 위반 |
                    | `ROUTINE404_3` | 404 | 없거나 비활성인 루틴 또는 다른 회원의 루틴 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "개인 루틴 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 값 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "수정할 개인 루틴을 찾을 수 없음")
    })
    ApiResponse<RoutineResDTO.Routine> updateRoutine(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "수정할 개인 루틴 ID", example = "12") Long routineId,
            RoutineReqDTO.UpdateRoutine request
    );

    @Operation(
            summary = "개인 루틴 삭제",
            description = """
                    인증 회원이 소유한 활성 개인 루틴을 삭제합니다.
                    수행 이력 보존을 위해 행은 비활성화하고, 반복 일정과 기본 루틴 참조를 해제합니다.
                    따라서 활성 루틴 30개 상한에서 제외되며 같은 기본 루틴을 다시 등록할 수 있습니다.
                    없거나 비활성인 루틴과 다른 회원의 루틴은 모두 ROUTINE404_3으로 응답합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "개인 루틴 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "삭제할 개인 루틴을 찾을 수 없음")
    })
    ApiResponse<Void> deleteRoutine(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "삭제할 개인 루틴 ID", example = "12") Long routineId
    );

    /**
     * 개인 루틴 벌크 생성 API 명세다.
     *
     * @param userDetails 인증 회원 정보
     * @param request 등록할 루틴 목록
     * @return 생성된 루틴과 생성 후 활성 루틴 총 개수
     */
    @Operation(
            summary = "개인 루틴 생성",
            description = """
                    루틴 추가 화면에서 선택하거나 직접 작성한 루틴을 한 번에 등록합니다.
                    전체 목록은 하나의 트랜잭션으로 저장되며, 한 건이라도 실패하면 모두 롤백됩니다.

                    규칙
                    - 활성 루틴은 기존 개수와 요청 개수를 합해 최대 30개까지 등록할 수 있습니다.
                    - 이름은 앞뒤 공백을 제거한 뒤 1~20자여야 하며 줄바꿈을 포함할 수 없습니다.
                    - templateId를 보내고 이름을 그대로 두면 그 기본 루틴을 고른 상태로 저장됩니다.
                      이름을 바꾸면 원본 선택이 해제되고 같은 카테고리의 사용자 루틴으로 등록됩니다.
                    - templateId 없이 보내면 지정한 카테고리에 직접 추가한 루틴이 되며,
                      사용자 루틴끼리는 같은 이름을 허용합니다.
                    - 같은 기본 루틴을 두 번 등록할 수 없습니다(요청 안에서도, 기존 루틴과도).
                    - 반복 요일을 생략하면 매일, 마감 시각을 생략하면 23:59이 적용됩니다.

                    ### categoryId / templateId 값

                    아래 ID는 `R__seed_routine.sql`이 고정 값으로 넣습니다. 사용자가 추가한 카테고리의
                    ID는 `GET /api/routines/categories`로, 최신 기본 루틴 목록은
                    `GET /api/routines/templates`로 확인하세요.

                    | categoryId | 카테고리 | templateId — 기본 제공 루틴 |
                    | --- | --- | --- |
                    | 1 | 운동 | 101 산책하기 · 102 스트레칭하기 · 103 홈트레이닝 하기 · 104 러닝하기 · 105 근력 운동하기 · 106 헬스장 다녀오기 |
                    | 2 | 건강 | 201 물 챙겨 마시기 · 202 영양제 챙겨 먹기 · 203 건강한 한 끼 먹기 · 204 채소 챙겨 먹기 · 205 단백질 챙겨 먹기 · 206 집밥 먹기 |
                    | 3 | 자기계발 | 301 책 읽기 · 302 뉴스 읽기 · 303 관심 분야 자료 정리하기 · 304 외국어 공부하기 · 305 강연 보고 기록 남기기 · 306 포트폴리오 작업하기 |
                    | 4 | 생활정리 | 401 침대 정리하기 · 402 쓰레기 버리기 · 403 책상 정리하기 · 404 설거지하기 · 405 빨래하기 · 406 방 정리하기 · 407 바닥 청소하기 |
                    | 5 | 마음관리 | 501 긍정 문장 남기기 · 502 일기 쓰기 · 503 감사 기록 쓰기 · 504 감정 기록 남기기 |
                    | 6 | 취미 | 601 사진 찍기 · 602 다이어리 쓰기 · 603 글쓰기 · 604 그림 그리기 · 605 요리하기 · 606 악기 연습하기 · 607 취미생활하기 |

                    `기타` 카테고리는 사용하지 않습니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 형식 검증 실패 — 이름이 비었거나 20자 초과·줄바꿈 포함, 루틴 0개 또는 31개 이상, 한 요청에 같은 templateId 두 번, 요일 중복 | 입력값 문제이므로 서버 메시지를 그대로 노출하지 않고 해당 입력 필드에 안내 |
                    | `ROUTINE400_1` | 400 | 이름이 앞뒤 공백 제거 후 1~20자를 벗어남 | 이름 입력란에 안내 |
                    | `ROUTINE400_3` | 400 | templateId가 요청한 categoryId에 속하지 않음 | 클라이언트 조합 오류. 목록을 다시 조회 |
                    | `ROUTINE403_1` | 403 | 다른 회원이 만든 카테고리를 지정 | 카테고리 목록을 다시 조회 |
                    | `MEMBER403_1` | 403 | 탈퇴·비활성 회원 | 로그아웃 처리 |
                    | `MEMBER404_1` | 404 | 토큰이 가리키는 회원이 없음 | 로그아웃 처리 |
                    | `ROUTINE404_1` | 404 | 카테고리가 없거나 비활성 | 카테고리 목록을 다시 조회 |
                    | `ROUTINE404_2` | 404 | 기본 제공 루틴이 없거나 비활성 | 기본 루틴 목록을 다시 조회 |
                    | `ROUTINE409_1` | 409 | 기존 + 요청 개수가 30개를 넘음 | "루틴은 최대 30개까지" 안내 후 선택 수를 줄이도록 유도 |
                    | `ROUTINE409_2` | 409 | 이미 등록한 기본 루틴을 같은 이름으로 다시 등록 | 해당 항목을 체크된 상태로 갱신. 목록의 alreadyAdded로 사전에 막을 수 있음 |

                    실패 시 전체가 롤백되므로 일부만 저장되는 경우는 없습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "개인 루틴 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값, 이름 규칙 또는 기본 루틴과 카테고리 불일치"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "미인증 요청, 탈퇴·비활성 회원 또는 다른 회원의 카테고리 사용"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원, 활성 카테고리 또는 활성 기본 제공 루틴을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "활성 루틴 30개 초과 또는 이미 등록한 기본 제공 루틴"
            )
    })
    ApiResponse<RoutineResDTO.RoutineCreateResult> createRoutines(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            RoutineReqDTO.CreateRoutines request
    );

    /**
     * 사용자 카테고리 추가 API 명세다.
     *
     * @param userDetails 인증 회원 정보
     * @param request 카테고리 이름과 색상
     * @return 생성된 카테고리
     */
    @Operation(
            summary = "루틴 카테고리 추가",
            description = """
                    인증 회원만 사용하는 사용자 카테고리를 추가합니다.

                    규칙
                    - 회원당 최대 5개까지 추가할 수 있습니다.
                    - 이름은 앞뒤 공백을 제거한 뒤 1~10자여야 하며 줄바꿈을 포함할 수 없습니다.
                    - 고정 카테고리 및 본인의 기존 카테고리와 같은 이름은 사용할 수 없습니다.
                    - color를 생략하면 "색 없음"으로 저장됩니다.

                    색상 칩은 RED, ORANGE, YELLOW, GREEN, BLUE, MAGENTA, BLACK 일곱 가지입니다.
                    실제 색상 값(HEX)은 테마·플랫폼마다 달라 서버가 정하지 않습니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `COMMON400_1` | 400 | 요청 형식 검증 실패 — 이름이 비었거나 10자 초과·줄바꿈 포함, 없는 색상 값 | 이름 입력란에 안내 |
                    | `ROUTINE400_2` | 400 | 이름이 앞뒤 공백 제거 후 1~10자를 벗어남 | 이름 입력란에 안내 |
                    | `MEMBER403_1` | 403 | 탈퇴·비활성 회원 | 로그아웃 처리 |
                    | `MEMBER404_1` | 404 | 토큰이 가리키는 회원이 없음 | 로그아웃 처리 |
                    | `ROUTINE409_3` | 409 | 이미 카테고리가 5개 | "카테고리는 최대 5개까지" 안내 |
                    | `ROUTINE409_4` | 409 | 고정 카테고리 또는 본인의 기존 카테고리와 이름 중복 | 이름 입력란에 중복 안내 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "루틴 카테고리 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값 또는 이름 규칙 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "미인증 요청 또는 탈퇴·비활성 회원"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "인증 토큰이 참조하는 회원을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "카테고리 5개 초과 또는 이름 중복"
            )
    })
    ApiResponse<RoutineResDTO.Category> createCategory(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            RoutineReqDTO.CreateCategory request
    );
}
