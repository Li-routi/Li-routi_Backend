package com.lirouti.domain.challenge.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.challenge.controller.docs.ChallengeControllerDocs;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.code.success.ChallengeSuccessCode;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/challenges")
public class ChallengeController implements ChallengeControllerDocs {
    private final ChallengeQueryService challengeQueryService;

    @Override
    @GetMapping
    public ApiResponse<ChallengeResDTO.Listing> getChallenges(
            @RequestParam(required = false) ChallengeCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        ChallengeResDTO.Listing result =
                challengeQueryService.getChallenges(category, keyword, cursor, size);
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_LIST_FETCH_SUCCESS, result);
    }

    @Override
    @GetMapping("/{challengeId}")
    public ApiResponse<ChallengeResDTO.Detail> getChallenge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId
    ) {
        // 상세는 비로그인도 볼 수 있어 principal이 null일 수 있다. 그때 memberId는 null → participating=false.
        Long memberId = (userDetails != null) ? userDetails.getMemberId() : null;
        ChallengeResDTO.Detail result = challengeQueryService.getChallenge(challengeId, memberId);
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_DETAIL_FETCH_SUCCESS, result);
    }
}
