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
public interface MyChallengeControllerDocs {

    @Operation(
            summary = "내가 참여 중인 챌린지 목록 조회",
            description = """
                    로그인한 회원이 현재 참여 중(active)인 챌린지를 최근 참여 순으로 조회합니다. 인증이 필요합니다.

                    참여 중인 것만 모아 보여주는 화면이라 페이지네이션이 없고, 통계 없이 심플 카드로 내려갑니다.

                    - category: 분류 필터. 생략하면 전체.
                    - keyword: 챌린지 이름 부분 검색.

                    응답 result: challenges(카드 목록).
                    카드 한 건: challengeId, name, description, imageUrl, category(enum → 프론트가 한글 변환).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 category 값"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "인증 필요(미인증)")
    })
    ApiResponse<ChallengeResDTO.MyListing> getMyChallenges(
            CustomUserDetails userDetails,
            @Parameter(description = "분류 필터 (HEALTH, EXERCISE, STUDY, LIFE, HOBBY). 생략 시 전체")
            ChallengeCategory category,
            @Parameter(description = "이름 부분 검색어")
            String keyword
    );
}
