package com.lirouti.domain.challenge.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.challenge.controller.docs.MyChallengeControllerDocs;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.code.success.ChallengeSuccessCode;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/challenges")
public class MyChallengeController implements MyChallengeControllerDocs {
    private final ChallengeQueryService challengeQueryService;

    @Override
    @GetMapping
    public ApiResponse<ChallengeResDTO.MyListing> getMyChallenges(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ChallengeCategory category,
            @RequestParam(required = false) String keyword
    ) {
        ChallengeResDTO.MyListing result =
                challengeQueryService.getMyChallenges(userDetails.getMemberId(), category, keyword);
        return ApiResponse.onSuccess(ChallengeSuccessCode.MY_CHALLENGE_LIST_FETCH_SUCCESS, result);
    }
}
