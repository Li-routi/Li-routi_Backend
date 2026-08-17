package com.lirouti.domain.challenge.controller.docs;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Challenge", description = "챌린지 조회 API")
public interface ChallengeControllerDocs {

    @Operation(
            summary = "챌린지 목록 조회 (무한 스크롤)",
            description = """
                    앱이 제공하는 활성 챌린지 목록을 최신순으로 조회합니다. 인증이 필요합니다.

                    무한 스크롤(커서 방식): 첫 요청은 cursor 없이 보내고, 응답의 nextCursor를 다음 요청의 cursor로 넘깁니다.
                    hasNext가 false(= nextCursor가 null)면 더 요청하지 않습니다.

                    - category: 분류 필터. 생략하면 전체(화면 칩의 '전체').
                    - keyword: 챌린지 이름 부분 검색.
                    - cursor: 직전 응답의 nextCursor(마지막으로 받은 challengeId). 첫 요청에는 생략합니다.
                    - size: 한 번에 가져올 개수. 기본값 20, 최대 50.

                    응답 result: challenges(카드 목록), nextCursor, hasNext.
                    카드 한 건: challengeId, name, description, imageUrl, category, routineCycle(DAILY/WEEKLY/MONTHLY),
                    reward(달성 시 부여되는 재화 수량), participantCount(참여자 수), verificationPostCount(인증 게시글 수).
                    category·routineCycle은 enum으로 내려가며 프론트가 한글로 변환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 category 값"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)")
    })
    ApiResponse<ChallengeResDTO.Listing> getChallenges(
            @Parameter(description = "분류 필터 (HEALTH, EXERCISE, STUDY, LIFE, HOBBY). 생략 시 전체")
            ChallengeCategory category,
            @Parameter(description = "이름 부분 검색어")
            String keyword,
            @Parameter(description = "직전 응답의 nextCursor(마지막으로 받은 challengeId). 첫 요청에는 생략")
            Long cursor,
            @Parameter(description = "한 번에 가져올 개수. 기본값 20, 최대 50")
            Integer size
    );

    @Operation(
            summary = "챌린지 상세 조회",
            description = """
                    챌린지 상세와 루틴 주기, 상단 통계(참여자 수·인증 게시글 수),
                    그리고 조회자의 참여 여부를 조회합니다. 인증이 필요합니다.

                    participating으로 '참여하기'/'인증하기' 버튼 상태를 정합니다.

                    응답 result: challengeId, name, description, imageUrl, category,
                    ### 인증하기 버튼은 두 값으로 그립니다
                    | participating | verifiedInCurrentPeriod | 버튼 |
                    | --- | --- | --- |
                    | false | - | 참여하기 |
                    | true | false | 인증하기 |
                    | true | true | 완료 (비활성) |

                    `verifiedInCurrentPeriod`는 **"오늘 인증했는지"가 아니라 "현재 주기 구간에
                    인증했는지"**입니다. `DAILY` 면 오늘, `WEEKLY` 면 이번 주(일~토),
                    `MONTHLY` 면 이번 달입니다. 주는 일요일에 시작합니다(KST).

                    **버튼이 잠긴 뒤 쓰기가 어떻게 되는지는 주기마다 다릅니다.**

                    | 주기 | `verifiedInCurrentPeriod=true` 인데 인증을 또 보내면 |
                    | --- | --- |
                    | `DAILY` | **덮어써집니다**(재인증). 사진·코멘트가 바뀌고 스트릭은 그대로 |
                    | `WEEKLY`·`MONTHLY` | **409** (`CHALLENGE409_6`) |

                    즉 `DAILY` 에서 버튼이 잠긴 것은 "다시 못 한다"가 아니라 **"오늘 몫은
                    끝났다"**는 뜻입니다. 사진을 바꾸고 싶으면 그대로 다시 보내면 됩니다.
                    주간·월간은 한 번 올리면 그 구간이 끝나 교체할 수 없습니다.

                    참여 중이 아니면 항상 false입니다 — 이탈한 뒤에는 인증할 수 없기 때문입니다.

                    **나갔다 다시 들어와도 그 구간에 인증했으면 true를 유지합니다.** 주기 1회는
                    참여 회차를 넘어 적용되므로, 재참여로 버튼을 다시 열 수 없습니다.

                    routineCycle(DAILY/WEEKLY/MONTHLY), reward(달성 시 부여되는 재화 수량),
                    participating(조회자 참여 여부), participantCount(참여자 수),
                    verificationPostCount(인증 게시글 수).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "challengeId 형식이 올바르지 않음(숫자 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 비활성 챌린지")
    })
    ApiResponse<ChallengeResDTO.Detail> getChallenge(
            CustomUserDetails userDetails,
            @Parameter(description = "챌린지 ID") Long challengeId
    );
}
