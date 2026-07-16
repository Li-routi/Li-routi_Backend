package com.lirouti.domain.challenge.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.challenge.controller.docs.ChallengeControllerDocs;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.ChallengeSortType;
import com.lirouti.domain.challenge.exception.code.success.ChallengeSuccessCode;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.global.apiPayload.ApiResponse;

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
            @RequestParam(required = false) ChallengeSortType sort
    ) {
        ChallengeResDTO.Listing result =
                challengeQueryService.getChallenges(category, keyword, sort);
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_LIST_FETCH_SUCCESS, result);
    }

    @Override
    @GetMapping("/{challengeId}")
    public ApiResponse<ChallengeResDTO.Detail> getChallenge(
            @PathVariable Long challengeId
    ) {
        ChallengeResDTO.Detail result = challengeQueryService.getChallenge(challengeId);
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_DETAIL_FETCH_SUCCESS, result);
    }
}
