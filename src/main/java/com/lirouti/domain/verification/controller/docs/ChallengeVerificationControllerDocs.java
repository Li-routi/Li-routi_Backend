package com.lirouti.domain.verification.controller.docs;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;

@Tag(name = "Challenge", description = "챌린지 인증 API")
public interface ChallengeVerificationControllerDocs {

    @Operation(
            summary = "챌린지 인증하기",
            description = """
                    참여 중인 챌린지에 오늘의 인증 사진과 코멘트를 등록합니다. 인증이 필요합니다.
                    홈 화면의 "빠른 인증"도 이 API를 사용합니다.

                    먼저 POST /api/media/presigned-url 로 발급받은 URL에 사진을 업로드한 뒤,
                    그 응답의 mediaKey를 그대로 보냅니다(전체 URL이 아니라 key입니다).

                    **인증 횟수는 챌린지의 주기(`routineCycle`)를 따릅니다.**

                    | 주기 | 한 구간 |
                    | --- | --- |
                    | `DAILY` | 하루 |
                    | `WEEKLY` | 그 주(일~토) |
                    | `MONTHLY` | 그 달 |

                    **한 구간에 한 번 내면 끝입니다.** 주기로 규칙이 갈리지 않습니다 — 주가
                    지나지 않았으면 주간도 하루와 똑같이 "이미 낸 것"입니다.

                    ### 사진을 바꾸려면 지우고 다시 올립니다

                    **덮어쓰기는 없습니다.** 이미 낸 구간에 또 보내면 409입니다.
                    사진을 바꾸려면 `DELETE /{verificationId}` 로 글을 내린 뒤 다시 인증하세요.
                    ⚠️ **리워드 회수는 아직 붙지 않았습니다.** 붙으면 지울 때 그 인증으로
                    받은 재화를 회수합니다. 그때까지는 회수 없이 다시 낼 수 있습니다.

                    | 상황 | 결과 |
                    | --- | --- |
                    | 그 구간에 인증이 있다 | 409 |
                    | 심사 보류(`PENDING`) 중이다 | 409 — 심사 중이어도 이미 낸 것입니다 |
                    | 탈퇴 후 재참여했다 | 409 — 나가기로 횟수를 늘릴 수 없습니다 |
                    | 신고 누적으로 가려졌다 | 409 — 아래 참고 |
                    | **본인이 지웠다** | **다시 낼 수 있습니다** |

                    **신고로 가려진 글이 있는 구간은 닫힌 채로 끝납니다.** 가려진 글은 지울 수
                    없고(404), 지우지 않은 인증은 구간을 점유하므로 다시 낼 수도 없습니다(409).
                    가려짐은 제재이므로 그 구간을 소진한 것으로 봅니다 — 다시 열어 주면 신고를
                    받은 사람이 사진만 바꿔 계속 낼 수 있습니다.

                    지운 뒤 다시 내면 보통 `reverified` 가 `true` 입니다(지운 행을 되살립니다).
                    다만 **지운 것이 지난 참여 회차의 인증이면** 되살리지 않고 새로 만들어
                    `false` 가 나갑니다 — 되살리면 회차가 옛 값으로 남기 때문입니다.
                    스트릭은 이미 오른 구간이면 다시 오르지 않습니다.

                    **주기 1회는 참여 회차를 넘어 적용됩니다.** 인증한 뒤 챌린지를 나갔다
                    다시 들어와도 그 구간에는 더 인증할 수 없습니다.
                    나가기로 인증 횟수를 늘릴 수 없습니다.

                    | 코드 | 언제 |
                    | --- | --- |
                    | `CHALLENGE409_5` | `DAILY` 인데 오늘 이미 인증함 |
                    | `CHALLENGE409_6` | 주간·월간인데 이번 구간에 이미 인증함 |

                    두 코드를 나눈 것은 **문구가 달라야 하기 때문**입니다. 주간 챌린지에
                    "오늘은 이미 인증했습니다"가 나가면 사용자가 내일 다시 눌러 보게 됩니다.

                    **스트릭도 주기 단위입니다.** 직전 구간에 인증했으면 1 오르고, 그보다
                    오래됐거나 첫 인증이면 1부터 시작합니다 — `DAILY` 는 연속 며칠,
                    `WEEKLY` 는 연속 몇 주, `MONTHLY` 는 연속 몇 달입니다.
                    표시 문구("N일 연속"/"N주 연속")는 `routineCycle` 을 보고 정하시면 됩니다.

                    **AI가 두 가지를 심사합니다.** 어느 쪽이든 반려되면 422이고,
                    사진은 저장되지 않으며 스트릭도 오르지 않습니다.
                    **반려된 사진은 S3에서도 즉시 삭제됩니다.**

                    | 코드 | 뜻 |
                    | --- | --- |
                    | `CHALLENGE422_1` | 챌린지 내용과 맞지 않는 사진 — 다시 찍어 올리면 됩니다 |
                    | `CHALLENGE422_2` | 공개 피드에 올릴 수 없는 사진(선정적·폭력적·타인 개인정보) |

                    두 기준은 판단 방향이 반대입니다. 챌린지 일치는 애매하면 통과시키고,
                    공개 가능 여부는 애매하면 반려합니다.

                    **심사기가 답을 못 주면 보류됩니다(reviewStatus=PENDING).** 장애·타임아웃이
                    그 경우이고, 막히는 것이 아니라 저장은 되며 스트릭도 오릅니다. 다만 아직
                    공개되지 않아 피드에는 안 보이고 **본인에게만** 보입니다.

                    보류일 때 imageUrl 은 공개 주소가 아니라 **한시적 서명 주소**입니다.
                    오래 들고 있다가 쓰면 만료됩니다.

                    서버가 10분마다 다시 심사하고, 통과하면 그때 공개됩니다. 24시간이 지나면
                    더 기다리지 않고 통과시킵니다 — 남의 장애로 반려하지 않습니다.

                    반려 응답의 message는 코드별 고정 문구입니다. 구체적인 판단 근거는
                    서버 로그에만 남고 응답에는 실리지 않습니다.

                    심사기에 장애가 나면 심사를 건너뛰고 통과시키며, 이때는 사진도 지우지 않습니다.

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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "참여 중이 아님 / 동시 중복 요청 / 나갔다 들어왔지만 이번 구간에 이미 인증함(CHALLENGE409_5) / 주간·월간인데 이번 구간에 이미 인증함(CHALLENGE409_6)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI 심사 반려 — 챌린지 불일치(CHALLENGE422_1) 또는 공개 불가(CHALLENGE422_2)")
    })
    ApiResponse<ChallengeVerificationResDTO.Verification> verify(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            ChallengeVerificationReqDTO.Verify request
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

                    각 카드에 likeCount(좋아요 수)와 liked(내가 눌렀는지)가 함께 나갑니다.
                    좋아요 수는 탈퇴 회원의 좋아요를 뺀 값입니다.

                    ### mine — 내가 올린 인증인지
                    `mine`은 **그 인증을 요청한 회원 본인이 올렸는지**입니다. 토큰의 회원과
                    작성자를 서버가 대조해 내려줍니다.

                    **닉네임으로 판단하지 마세요.** 닉네임에는 유니크 제약이 없어(회원 유니크는
                    이메일과 소셜 식별자뿐) 동명이인이 생기면 남의 글이 내 글로 보입니다.
                    그 값으로 삭제·신고 버튼을 그리면 그대로 사고가 됩니다.

                    응답 result: verifications[{ verificationId, nickname, imageUrl, content,
                    verifiedAt, likeCount, liked, mine }], nextCursor, hasNext.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 비활성 챌린지")
    })
    ApiResponse<ChallengeVerificationResDTO.Feed> getVerificationFeed(
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

                    ### 전체 회차가 나옵니다
                    그만뒀다 다시 참여해도 **지난 참여의 인증이 그대로 보입니다.** 이탈은
                    기록을 지우지 않기 때문입니다.

                    각 항목의 `participationRound`가 응답의 `currentParticipationRound`보다
                    작으면 **지난 참여**의 기록입니다. 이 둘을 비교해 "이번 참여 / 지난 참여"로
                    묶어 보여주세요.

                    **스트릭은 현재 회차 기준이라 목록과 기준이 다릅니다.** 재참여 직후에는
                    "0일 연속" 옆에 지난 참여 기록이 놓일 수 있으니, 회차로 구분해 주세요.

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
                    verifiedAt, likeCount, participationRound }], currentStreak,
                    currentParticipationRound, nextCursor, hasNext.

                    reviewStatus 로 심사 상태가 함께 내려갑니다. PENDING 이면 아직 공개되지 않은
                    인증이라 피드에는 없고 여기서만 보이며, imageUrl 은 한시적 서명 주소입니다.
                    화면에는 "심사 중" 으로 표시해 주세요.

                    status=PENDING 으로 좁히면 심사 중인 것만 내려갑니다. 생략하면 전부입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "그 챌린지에 참여한 이력이 없음")
    })
    ApiResponse<ChallengeVerificationResDTO.MyVerifications> getMyVerifications(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 요청에서는 생략") Long cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 50)") Integer size,
            @Parameter(description = "심사 상태로 좁힌다. 생략하면 전부. PENDING 이면 심사 중인 것만")
            ReviewStatus status
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
    ApiResponse<ChallengeVerificationResDTO.Like> like(
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
    ApiResponse<ChallengeVerificationResDTO.Like> unlike(
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

                    ### 사유 선택은 필수입니다

                    | `reportType` | 화면 문구(참고) | `reason` |
                    | --- | --- | --- |
                    | `IRRELEVANT` | 실제 루틴 수행과 무관한 사진이에요 | 보내지 않습니다 |
                    | `REUSED` | 예전에 인증했던 사진을 재사용했어요 | 보내지 않습니다 |
                    | `STOLEN` | 타인의 사진을 도용한 것 같아요 | 보내지 않습니다 |
                    | `SPAM` | 스팸 또는 광고성 콘텐츠예요 | 보내지 않습니다 |
                    | `ETC` | 기타 | **필수** (1~100자) |

                    **화면 문구는 클라이언트가 가집니다.** 서버가 내려주면 문구를 고칠 때마다
                    서버 배포가 필요해집니다.

                    `ETC` 가 아닌데 `reason` 을 함께 보내도 **거절하지 않고 버립니다.** 라디오를
                    바꿀 때 입력란 값을 지우지 않고 보내는 실수 때문에 신고 자체가 실패하는 것이
                    더 나쁘다고 봤습니다. 다만 저장하지도 않습니다.

                    **사유는 숨김 판정에 쓰이지 않습니다.** 가려지는 기준은 그대로 신고 **건수**
                    입니다. 사유는 우선 기록만 하고, 쌓인 뒤에 판단합니다.

                    같은 인증을 두 번 신고하면 409입니다. 신고 취소는 제공하지 않습니다.

                    응답 result: reportId, verificationId.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "reportType 누락 / ETC 인데 reason 없음 / reason 이 100자 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "해당 챌린지에 그 인증이 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 신고한 인증")
    })
    ApiResponse<ChallengeVerificationResDTO.Report> report(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "신고할 인증 ID") Long verificationId,
            ChallengeVerificationReqDTO.Report request
    );

    @Operation(
            summary = "인증 메모 수정",
            description = """
                    내가 올린 인증의 **메모(코멘트)만** 수정합니다. 인증이 필요합니다.

                    피드 응답의 `mine`이 `true`인 카드에만 이 진입점을 노출하세요.

                    ### 사진은 바뀌지 않습니다
                    사진을 바꾸려면 **그날 다시 인증**하세요(POST). 사진은 수행의 증거라
                    교체하면 AI 심사를 다시 거치고, 그래서 경로가 다릅니다.
                    이 API는 사진도 인증 시각도 건드리지 않습니다 — 피드 순서가 흔들리지 않습니다.

                    ### 날짜 제한이 없습니다
                    어제 이전에 쓴 메모도 고칠 수 있습니다. 메모는 사진에 덧붙이는 말이라
                    나중에 고쳐도 "그날 수행했다"는 사실이 흔들리지 않기 때문입니다.
                    (사진 교체가 당일로 제한되는 것과 다릅니다.)

                    ### 메모 비우기
                    `content`를 비우거나 보내지 않으면 **메모가 지워집니다.** 공백만 보내도
                    같습니다. 처음 인증할 때도 선택 값이라 나중에 지우지 못할 이유가 없습니다.

                    ### 404가 나는 경우
                    **내 인증이 아니거나, 없거나, 신고 누적으로 가려진 경우**입니다. 셋을
                    구분해 알려주지 않습니다 — 구분하면 응답만으로 그 id의 존재와 작성자가
                    드러납니다. 가려진 인증은 작성자 본인에게도 보이지 않으므로 수정도 막습니다.

                    스트릭·좋아요·신고는 영향받지 않습니다. 행이 사라지지 않고 인증일도
                    그대로라 그것들이 참조하는 값이 하나도 바뀌지 않습니다.

                    응답 result: verificationId, content(비웠으면 null).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "코멘트가 255자를 넘음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "내 인증이 아니거나 없거나 가려진 인증")
    })
    ApiResponse<ChallengeVerificationResDTO.MemoUpdate> updateMemo(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "인증 ID") Long verificationId,
            ChallengeVerificationReqDTO.UpdateMemo request
    );

    @Operation(
            summary = "인증 게시글 삭제",
            description = """
                    내가 올린 인증 게시글을 내립니다. 본인 글만 지울 수 있습니다.

                    남의 글, 없는 글, 경로의 챌린지와 다른 인증, 신고로 가려진 글은 **전부 같은
                    404** 입니다 — 존재 여부를 알려 주지 않습니다.

                    **인증을 취소하는 것이 아니라 글을 내리는 것입니다.** 스트릭과 인증 기록은
                    그대로 남습니다.

                    다만 **그 구간은 다시 열립니다.** 지우면 상세의 `verifiedInCurrentPeriod` 가
                    `false` 로 돌아와 버튼이 "인증하기" 로 바뀌고, 다시 인증하면 그 자리가
                    되살아납니다. 사진을 바꾸는 유일한 방법이기도 합니다.

                    사진은 S3 에서도 지웁니다. 공개 주소라 조회에서 빼는 것만으로는 URL 을 아는
                    사람이 계속 볼 수 있기 때문입니다.

                    **신고 누적으로 가려진 글은 지울 수 없습니다(404).** 지워서 신고 누적을
                    회피하는 길을 막습니다. 그 구간은 다시 인증할 수도 없어(409) 닫힌 채로
                    끝납니다 — 가려짐이 제재이기 때문입니다.

                    **스트릭은 그대로입니다.** 사진을 올려 심사를 통과했다면 루틴은 실제로 한 것이라,
                    공개를 원치 않아 내렸다고 "며칠째 이어왔다"까지 되돌리지는 않습니다.

                    신고가 쌓여 가려진 글은 지울 수 없습니다(404). 본인에게도 안 보이는 글이고,
                    지워서 신고 누적을 회피하는 길도 막습니다.

                    이미 지운 글을 다시 지우면 성공으로 답합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(미로그인)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "없는 인증 · 내 글이 아님 · 경로의 챌린지와 다른 인증 · "
                            + "신고로 가려진 글 — 전부 같은 404 다(CHALLENGE404_2)")
    })
    ApiResponse<ChallengeVerificationResDTO.Deletion> deleteVerification(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId,
            @Parameter(description = "삭제할 인증 ID") Long verificationId
    );
}
