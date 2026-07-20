package com.lirouti.domain.challenge.controller.docs;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.global.apiPayload.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Challenge", description = "챌린지 조회 API")
public interface ChallengeControllerDocs {

    @Operation(
            summary = "챌린지 목록 조회 (무한 스크롤)",
            description = """
                    앱이 제공하는 활성 챌린지 목록을 최신순으로 조회합니다. 로그인 없이 사용할 수 있습니다.

                    무한 스크롤(커서 방식): 첫 요청은 cursor 없이 보내고, 응답의 nextCursor를 다음 요청의 cursor로 넘깁니다.
                    hasNext가 false(= nextCursor가 null)면 더 요청하지 않습니다.

                    - category: 분류 필터. 생략하면 전체(화면 칩의 '전체').
                    - keyword: 챌린지 이름 부분 검색.
                    - cursor: 직전 응답의 nextCursor(마지막으로 받은 challengeId). 첫 요청에는 생략합니다.
                    - size: 한 번에 가져올 개수. 기본값 20, 최대 50.

                    응답 result: challenges(카드 목록), nextCursor, hasNext.
                    카드 한 건: challengeId, name, description, imageUrl, category. (참여자 수는 상세에서 제공)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 category 값")
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
            description = "챌린지 상세와 전체 참여자 수, 오늘 완료자 수를 조회합니다. 로그인 없이 사용할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "challengeId 형식이 올바르지 않음(숫자 아님)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 비활성 챌린지")
    })
    ApiResponse<ChallengeResDTO.Detail> getChallenge(
            @Parameter(description = "챌린지 ID") Long challengeId
    );
}
