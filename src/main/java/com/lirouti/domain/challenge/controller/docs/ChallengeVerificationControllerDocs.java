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

                    **사진이 챌린지 내용과 맞는지 AI가 심사합니다.** 맞지 않다고 판정되면 422로
                    반려되고 사진은 저장되지 않으며 스트릭도 오르지 않습니다. 반려 사유는 응답
                    message로 내려갑니다.

                    심사기에 장애가 나면 심사를 건너뛰고 통과시킵니다. 외부 서비스 문제로 인증
                    자체가 막히지 않게 한 것이라, 반려는 "판정을 받았고 맞지 않았다"일 때만 납니다.

                    응답 result: verificationId, challengeId, verifiedDate, verifiedAt,
                    imageUrl(조립된 공개 URL), content, currentStreak, reverified.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공(덮어쓰기 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "발급 규칙에 맞지 않는 미디어 key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "참여 중이 아님 / 동시 중복 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI 심사에서 챌린지 내용과 맞지 않다고 판정")
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

                    **내가 신고한 인증은 이 목록에서 빠집니다.** 신고는 사진을 지우는 것이 아닙니다.
                    신고가 적을 때는 신고자 본인에게만 가려지고 다른 회원에게는 그대로 보이지만,
                    신고가 일정 수만큼 쌓이면 전체 회원에게 가려집니다.

                    각 카드에 likeCount(좋아요 수)와 liked(내가 눌렀는지)가 함께 나갑니다(#63).
                    좋아요 수는 탈퇴 회원의 좋아요를 뺀 값입니다.

                    응답 result: verifications[{ verificationId, nickname, imageUrl, content,
                    verifiedAt, likeCount, liked }], nextCursor, hasNext.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 비활성 챌린지")
    })
    ApiResponse<ChallengeResDTO.Feed> getVerificationFeed(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 요청에서는 생략") Long cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 50)") Integer size
    );

    @Operation(
            summary = "내가 인증한 게시물만 조회",
            description = """
                    그 챌린지에서 **내가 남긴 인증만** 최신순으로 조회합니다. 인증이 필요합니다.

                    커서 페이지네이션은 피드와 같습니다(커서 값은 verificationId).

                    **현재 회차의 인증만 나옵니다.** 그만뒀다 다시 참여하면 회차가 올라가고
                    지난 회차의 인증은 이 목록에 포함되지 않습니다. 스트릭·오늘 완료 여부와
                    같은 기준입니다.

                    **그만둔 챌린지도 조회됩니다.** 마지막으로 참여했던 회차의 기록이 그대로 보입니다.
                    한 번도 참여한 적이 없으면 빈 목록이 아니라 409입니다.

                    피드와 달리 nickname이 없습니다(전부 본인입니다). 대신 verifiedDate가 있어
                    날짜별로 묶어 보여줄 수 있습니다. 당일 재인증은 verifiedAt만 갱신되므로
                    두 값이 다를 수 있습니다.

                    currentStreak은 저장값이 아니라 **오늘 기준으로 다시 판정한 값**입니다.
                    마지막 인증이 이틀 전이면 0으로 내려갑니다.

                    likeCount는 있고 liked는 없습니다. 같은 인증이 피드에 나올 때와 좋아요 수가 같아야 하지만,
                    "내가 눌렀는지"는 자기 게시물에서 쓸 데가 없습니다.

                    응답 result: verifications[{ verificationId, imageUrl, content, verifiedDate,
                    verifiedAt, likeCount }], currentStreak, nextCursor, hasNext.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "그 챌린지에 참여한 이력이 없음")
    })
    ApiResponse<ChallengeResDTO.MyVerifications> getMyVerifications(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 요청에서는 생략") Long cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 50)") Integer size
    );

    @Operation(
            summary = "인증 게시물 좋아요",
            description = """
                    인증 게시물에 좋아요를 남깁니다. 인증이 필요합니다.

                    좋아요는 챌린지가 아니라 **인증 한 건**에 붙습니다. 같은 사진이 피드에도
                    내 인증 목록에도 나오지만 좋아요 수는 하나입니다.

                    **이미 눌러둔 상태에서 다시 불러도 성공입니다.** 좋아요는 토글이라 같은 요청이
                    두 번 오는 것이 정상 사용이라, 오류 대신 최종 상태를 돌려줍니다.
                    응답의 likeCount·liked로 화면을 갱신하면 되며 재조회가 필요 없습니다.

                    자기 인증에도 누를 수 있습니다.

                    응답 result: verificationId, likeCount, liked.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 성공(이미 눌린 상태 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그 챌린지에 없는 인증")
    })
    ApiResponse<ChallengeResDTO.Like> like(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "인증 ID") Long verificationId
    );

    @Operation(
            summary = "인증 게시물 좋아요 취소",
            description = """
                    좋아요를 취소합니다. 인증이 필요합니다.

                    **누르지 않은 상태에서 불러도 성공입니다**(멱등). 다만 그 챌린지에 없는
                    인증 ID를 보내면 404입니다 — 멱등한 것은 "좋아요가 없는 경우"이지
                    "인증이 없는 경우"가 아닙니다.

                    취소는 기록을 남기지 않고 행을 지웁니다. 다시 눌러도 제약에 걸리지 않습니다.

                    응답 result: verificationId, likeCount, liked(false).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공(눌러둔 적 없는 경우 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그 챌린지에 없는 인증")
    })
    ApiResponse<ChallengeResDTO.Like> unlike(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "인증 ID") Long verificationId
    );

    @Operation(
            summary = "인증 신고하기",
            description = """
                    피드의 인증을 신고합니다. 인증이 필요합니다.

                    **신고해도 사진은 삭제되지 않습니다.** 다만 효과가 두 단계입니다.

                    신고 즉시 **신고자 본인의 조회**에서 그 인증이 빠집니다. 그리고 신고가
                    **일정 수만큼 쌓이면 전체 회원에게** 가려집니다 — 작성자 본인의 내 인증 목록과
                    챌린지 상세의 인증 게시글 수에서도 빠집니다. 이때도 작성자의 스트릭과 인증
                    기록은 그대로입니다. 가려지는 것은 노출뿐입니다.

                    reason(신고 사유)은 선택입니다. 사유 선택 없이 바로 신고할 수 있습니다.

                    같은 인증을 두 번 신고하면 409입니다. 신고 취소는 제공하지 않습니다.

                    응답 result: reportId, verificationId.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "신고 사유가 255자를 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "해당 챌린지에 그 인증이 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 신고한 인증")
    })
    ApiResponse<ChallengeResDTO.Report> report(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "신고할 인증 ID") Long verificationId,
            ChallengeReqDTO.Report request
    );
}
