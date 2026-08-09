package com.lirouti.domain.group.controller.docs;

import com.lirouti.domain.group.dto.response.GroupInteractionResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "그룹 상호작용",
        description = "그룹 루틴 인증 게시물의 '아쉬워요' 반응과, 그룹원을 독려하는 '찌르기' API입니다."
)
public interface GroupInteractionControllerDocs {

    /**
     * 그룹 루틴 인증 아쉬워요 등록 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param groupId 인증 게시물이 속한 그룹 ID
     * @param verificationId 아쉬워요를 남길 인증 게시물 ID
     * @return 현재 아쉬워요 수와 상태
     */
    @Operation(
            summary = "그룹 루틴 인증 아쉬워요",
            description = """
                    그룹 루틴 인증 게시물에 '아쉬워요' 반응을 남깁니다.
                    같은 게시물에 이미 '좋아요'를 눌러둔 상태였다면 좋아요는 자동으로 취소되고
                    아쉬워요로 대체됩니다(좋아요·아쉬워요는 한 게시물에 동시에 있을 수 없음).

                    ### 동작 방식

                    - 멱등 등록입니다. 이미 아쉬워요를 남긴 게시물에 다시 요청해도 새로 추가되지 않고
                      현재 상태를 그대로 반환합니다.
                    - 아쉬워요 첫 등록 시, 작성자 본인이 아니면 작성자에게
                      `GROUP_VERIFICATION_DISAPPOINTED` 알림이 전송됩니다.
                    - 좋아요가 취소되며 작성자의 그룹 내 누적 좋아요 수도 함께 감소합니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 | 재로그인 유도 |
                    | `GROUP403_1` | 403 | 비활성화된 그룹 | 그룹 목록으로 이동 |
                    | `GROUP403_2` | 403 | 요청자가 해당 그룹의 활성 구성원이 아님 | 그룹 목록으로 이동 |
                    | `GROUP404_1` | 404 | 그룹이 존재하지 않음 | 그룹 목록으로 이동 |
                    | `MEMBER404_1` | 404 | 인증 토큰이 참조하는 회원을 찾을 수 없음 | 로그아웃 처리 |
                    | `VERIFICATION404_3` | 404 | 해당 그룹에 이 인증 게시물이 없음 | 목록을 새로고침 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "아쉬워요 등록 성공(멱등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹 또는 비활성 구성원"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹, 회원 또는 인증 게시물을 찾을 수 없음")
    })
    ApiResponse<GroupInteractionResDTO.Disappointment> disappoint(
            @Parameter(hidden = true) CustomUserDetails user,
            @Parameter(description = "그룹 ID", example = "1") Long groupId,
            @Parameter(description = "아쉬워요를 남길 인증 게시물 ID", example = "10") Long verificationId
    );

    /**
     * 그룹 루틴 인증 아쉬워요 취소 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param groupId 인증 게시물이 속한 그룹 ID
     * @param verificationId 아쉬워요를 취소할 인증 게시물 ID
     * @return 현재 아쉬워요 수와 상태
     */
    @Operation(
            summary = "그룹 루틴 인증 아쉬워요 취소",
            description = """
                    이미 남긴 '아쉬워요' 반응을 취소합니다.
                    아쉬워요를 남긴 적이 없어도 항상 200으로 응답합니다(멱등 취소).

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 | 재로그인 유도 |
                    | `GROUP403_1` | 403 | 비활성화된 그룹 | 그룹 목록으로 이동 |
                    | `GROUP403_2` | 403 | 요청자가 해당 그룹의 활성 구성원이 아님 | 그룹 목록으로 이동 |
                    | `GROUP404_1` | 404 | 그룹이 존재하지 않음 | 그룹 목록으로 이동 |
                    | `MEMBER404_1` | 404 | 인증 토큰이 참조하는 회원을 찾을 수 없음 | 로그아웃 처리 |
                    | `VERIFICATION404_3` | 404 | 해당 그룹에 이 인증 게시물이 없음 | 목록을 새로고침 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "아쉬워요 취소 성공(멱등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹 또는 비활성 구성원"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹, 회원 또는 인증 게시물을 찾을 수 없음")
    })
    ApiResponse<GroupInteractionResDTO.Disappointment> undisappoint(
            @Parameter(hidden = true) CustomUserDetails user,
            @Parameter(description = "그룹 ID", example = "1") Long groupId,
            @Parameter(description = "아쉬워요를 취소할 인증 게시물 ID", example = "10") Long verificationId
    );

    /**
     * 그룹원 찌르기 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param groupId 대상 그룹 ID
     * @param recipientId 찌를 그룹원의 회원 ID
     * @return 찌르기 결과
     */
    @Operation(
            summary = "그룹원 찌르기",
            description = """
                    아직 루틴을 수행하지 않은 그룹원을 독려하기 위해 '찌르기' 알림을 보냅니다.
                    수신자에게 `GROUP_MEMBER_POKED` 알림이 전송됩니다.

                    ### 규칙

                    - 자기 자신은 찌를 수 없습니다.
                    - 같은 그룹에서 같은 발신자→수신자 조합은 하루(한국 시간 기준) 한 번만 가능합니다.
                      이미 오늘 찌른 상대를 다시 찌르면 409로 응답합니다.
                    - 발신자·수신자 모두 해당 그룹의 활성 구성원이어야 합니다.
                    - 서로 다른 수신자에게는 같은 날 여러 번 찌를 수 있습니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `COMMON400_1` | 400 | recipientId가 자기 자신 | "자신은 찌를 수 없어요" 안내 |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 | 재로그인 유도 |
                    | `GROUP403_1` | 403 | 비활성화된 그룹 | 그룹 목록으로 이동 |
                    | `GROUP403_2` | 403 | 발신자 또는 수신자가 해당 그룹의 활성 구성원이 아님 | 멤버 목록을 새로고침 |
                    | `GROUP404_1` | 404 | 그룹이 존재하지 않음 | 그룹 목록으로 이동 |
                    | `MEMBER404_1` | 404 | 인증 토큰이 참조하는 회원을 찾을 수 없음 | 로그아웃 처리 |
                    | `COMMON409_1` | 409 | 오늘 이미 같은 그룹원을 찌름 | "오늘은 이미 찔렀어요" 안내, 버튼 비활성화 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "찌르기 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "자기 자신을 찌르려 함"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "비활성 그룹 또는 비활성 구성원"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹 또는 회원을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "오늘 이미 같은 상대를 찌름")
    })
    ApiResponse<GroupInteractionResDTO.Poke> poke(
            @Parameter(hidden = true) CustomUserDetails user,
            @Parameter(description = "그룹 ID", example = "1") Long groupId,
            @Parameter(description = "찌를 그룹원의 회원 ID", example = "9002") Long recipientId
    );
}
