package com.lirouti.domain.challenge.controller.docs;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Challenge", description = "챌린지 참여 API")
public interface ChallengeParticipationControllerDocs {

    @Operation(
            summary = "챌린지 참여",
            description = """
                    로그인한 회원이 챌린지에 참여합니다. 인증이 필요합니다.

                    처음 참여하면 새 참여를 만들고, 예전에 그만뒀던 챌린지면 기존 참여를 되살립니다(재참여).
                    재참여 시 회차(participationRound)가 1 올라가고 스트릭은 0으로 초기화됩니다.

                    응답 result: challengeId, participating(true), participationRound.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "참여 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 비활성 챌린지"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 참여 중")
    })
    ApiResponse<ChallengeResDTO.Participation> participate(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId
    );

    @Operation(
            summary = "챌린지 이탈",
            description = """
                    로그인한 회원이 참여 중인 챌린지를 그만둡니다. 인증이 필요합니다.

                    참여 상태(active)만 끄고 참여 이력·인증은 보존합니다. 참여 중이 아니면 409.

                    응답 result: challengeId, participating(false), participationRound.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이탈 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "참여 중이 아님")
    })
    ApiResponse<ChallengeResDTO.Participation> leave(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId
    );
}
