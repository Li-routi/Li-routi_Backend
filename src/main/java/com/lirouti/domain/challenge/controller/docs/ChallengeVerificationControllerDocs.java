package com.lirouti.domain.challenge.controller.docs;

import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Challenge", description = "챌린지 인증 API")
public interface ChallengeVerificationControllerDocs {

    @Operation(
            summary = "챌린지 인증하기",
            description = """
                    참여 중인 챌린지에 오늘의 인증 사진과 코멘트를 등록합니다. 인증이 필요합니다.
                    홈 화면의 "빠른 인증"도 이 API를 사용합니다.

                    먼저 POST /api/media/presigned-url 로 발급받은 URL에 사진을 업로드한 뒤,
                    그 응답의 mediaKey를 그대로 보냅니다(전체 URL이 아니라 key입니다).

                    하루에 한 번만 인증할 수 있습니다. 오늘 이미 인증했다면 새 인증이 만들어지는 대신
                    사진·코멘트가 덮어써지고(reverified=true), 이때 스트릭은 오르지 않습니다.
                    어제 인증했으면 스트릭이 1 오르고, 그보다 오래됐거나 첫 인증이면 1부터 시작합니다.

                    응답 result: verificationId, challengeId, verifiedDate, verifiedAt,
                    imageUrl(조립된 공개 URL), content, currentStreak, reverified.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공(덮어쓰기 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "발급 규칙에 맞지 않는 미디어 key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "참여 중이 아님 / 동시 중복 요청")
    })
    ApiResponse<ChallengeResDTO.Verification> verify(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            ChallengeReqDTO.Verify request
    );

    @Operation(
            summary = "최신 인증 피드 조회",
            description = """
                    챌린지의 인증을 최신순으로 조회합니다. 인증이 필요합니다.

                    무한 스크롤용 커서 페이지네이션입니다. 첫 요청은 cursor 없이 보내고,
                    응답의 nextCursor를 다음 요청의 cursor로 넘깁니다. hasNext가 false면 멈춥니다.
                    커서 값은 verificationId입니다.

                    탈퇴한 회원의 인증은 제외됩니다. 같은 날 그만뒀다 다시 참여해 인증한 경우는
                    별개의 인증이므로 둘 다 보입니다.

                    응답 result: verifications[{ verificationId, nickname, imageUrl, content, verifiedAt }],
                    nextCursor, hasNext.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 비활성 챌린지")
    })
    ApiResponse<ChallengeResDTO.Feed> getVerificationFeed(
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 요청에서는 생략") Long cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 50)") Integer size
    );
}
