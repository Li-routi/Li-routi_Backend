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

                    사진은 그 방 멤버만 볼 수 있는 비공개 저장소에 들어갑니다. 이 응답은 저장된
                    key만 돌려주므로 **사진을 보려면 아래 목록 API를 호출하세요** — 거기서
                    서명된 조회 주소를 내려줍니다.
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

                    사진은 비공개 저장소에 들어가고 이 응답은 저장된 key만 돌려줍니다.
                    **개인 인증 사진을 다시 조회하는 API는 없습니다** — 그 사진을 보여주는
                    화면이 없다는 기획 판단입니다. 완료 여부는 루틴 목록의 완료 표시로
                    확인하세요. (그룹 인증은 방 멤버가 함께 보므로 목록 API가 있습니다.)
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

    @Operation(
            summary = "그룹 루틴 인증 목록 조회",
            description = """
                    그 그룹 루틴의 인증을 최신순으로 조회합니다. **방 멤버 전원의 인증이 함께
                    나옵니다** — 누가 했는지 보는 화면이라 작성자(memberId·nickname)를 싣습니다.

                    **그 방의 활성 멤버만 조회할 수 있습니다.** 아니면 403입니다.

                    ### 사진 주소는 한시적입니다
                    `imageUrl`은 비공개 저장소를 여는 **서명된 주소**이고 유효 시간이 지나면
                    만료됩니다(기본 15분). **저장해 두고 재사용하지 마세요** — 필요할 때 이
                    목록을 다시 호출하면 새 주소를 받습니다.

                    ### 페이지네이션
                    첫 요청은 `cursor` 없이 보내고, 응답의 `nextCursor`를 다음 요청의 `cursor`로
                    넘깁니다. `hasNext`가 false면 더 요청하지 않습니다.
                    `size`는 51 이상이면 50으로 잘립니다. **생략하거나 0 이하를 보내면
                    잘리는 것이 아니라 기본값 20이 적용됩니다** — `size=0`으로 빈 목록을
                    받을 수는 없습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그 방의 활성 멤버가 아님 / 인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그 그룹에 없는 루틴 / 없는 그룹")
    })
    ApiResponse<VerificationResDTO.GroupRoutineFeed> getGroupRoutineVerifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long groupId,
            Long routineId,
            Long cursor,
            Integer size
    );

    @Operation(
            summary = "그룹 루틴 인증 게시물 좋아요",
            description = """
                    그룹 루틴 인증 게시물에 좋아요를 남깁니다. 해당 그룹의 활성 멤버만 호출할 수 있습니다.

                    이미 좋아요한 게시물에 다시 호출해도 성공합니다. 좋아요는 인증 한 건에 붙고,
                    응답의 likeCount·liked로 화면을 재조회 없이 갱신할 수 있습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 성공(이미 좋아요한 상태 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그룹의 활성 멤버가 아님 / 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹에 없는 인증 게시물")
    })
    ApiResponse<VerificationResDTO.GroupRoutineLike> likeGroupRoutineVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long groupId,
            Long verificationId
    );

    @Operation(
            summary = "그룹 루틴 인증 게시물 좋아요 취소",
            description = """
                    그룹 루틴 인증 게시물의 좋아요를 취소합니다. 해당 그룹의 활성 멤버만 호출할 수 있습니다.

                    좋아요가 없는 상태에서 호출해도 성공합니다. 실제 Like 행만 물리 삭제합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 취소 성공(좋아요가 없는 상태 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "그룹의 활성 멤버가 아님 / 인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹에 없는 인증 게시물")
    })
    ApiResponse<VerificationResDTO.GroupRoutineLike> unlikeGroupRoutineVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long groupId,
            Long verificationId
    );

}
