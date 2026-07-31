package com.lirouti.domain.verification.controller.docs;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "루틴 인증", description = "그룹 루틴·개인 루틴 인증 API")
public interface RoutineVerificationControllerDocs {

    @Operation(
            summary = "그룹 루틴 인증",
            description = """
                    오늘 배정된 그룹 루틴을 사진으로 인증합니다. 인증이 곧 완료 처리입니다.

                    먼저 POST /api/media/presigned-url 로 purpose=GROUP_ROUTINE_VERIFICATION 을
                    지정해 발급받은 URL에 사진을 올린 뒤, 그 응답의 mediaKey를 보냅니다.

                    **수행 가능 시간에만 인증할 수 있습니다.** 시작 전이거나 종료 시각이 지나면
                    409입니다. 시간이 지나 MISSED로 넘어간 것도 되돌릴 수 없습니다.

                    **이미 인증한 루틴은 다시 인증할 수 없습니다.** 챌린지와 달리 사진 교체를
                    허용하지 않습니다.

                    사진은 그 방 멤버만 볼 수 있는 비공개 저장소에 들어갑니다. **지금은 조회
                    URL을 내려주지 않습니다** — 서명 발급이 아직 구현되지 않아 저장까지만 됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "발급 규칙에 맞지 않는 미디어 key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "오늘 배정된 그 루틴이 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "수행 시간이 아님 / 이미 인증함 / 동시 중복 요청")
    })
    ApiResponse<VerificationResDTO.GroupRoutine> verifyGroupRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long groupId,
            Long routineId,
            VerificationReqDTO.Verify request
    );

    @Operation(
            summary = "개인 루틴 인증",
            description = """
                    내 루틴을 사진으로 인증합니다. 홈 화면의 내 루틴 카드에서 호출합니다.

                    먼저 POST /api/media/presigned-url 로 purpose=MEMBER_ROUTINE_VERIFICATION 을
                    지정해 발급받은 URL에 사진을 올린 뒤, 그 응답의 mediaKey를 보냅니다.

                    **등록할 때 정한 요일에만 인증할 수 있습니다.** 오늘이 그 루틴의 수행 요일이
                    아니면 409입니다.

                    **하루에 한 번입니다.** 이미 오늘 인증했으면 409이며, 사진 교체는 허용하지
                    않습니다.

                    사진은 본인만 볼 수 있는 비공개 저장소에 들어갑니다. **지금은 조회 URL을
                    내려주지 않습니다** — 서명 발급이 아직 구현되지 않아 저장까지만 됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "발급 규칙에 맞지 않는 미디어 key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "내 루틴이 아니거나 없는 루틴"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "수행 요일이 아님 / 이미 인증함 / 동시 중복 요청")
    })
    ApiResponse<VerificationResDTO.MemberRoutine> verifyMemberRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long routineId,
            VerificationReqDTO.Verify request
    );
}
