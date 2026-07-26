package com.lirouti.domain.challenge.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.challenge.controller.docs.ChallengeVerificationControllerDocs;
import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.exception.code.success.ChallengeSuccessCode;
import com.lirouti.domain.challenge.service.command.ChallengeCommandService;
import com.lirouti.domain.challenge.service.query.ChallengeQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 챌린지 인증과 인증 피드.
 * 목록·상세(GET /api/challenges, /api/challenges/{id})와 달리 이 경로는 공개되어 있지 않다
 * — SecurityConfig가 챌린지의 중첩 경로를 인증 필요로 두므로 피드 조회도 로그인이 필요하다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/challenges/{challengeId}/verifications")
public class ChallengeVerificationController implements ChallengeVerificationControllerDocs {
    private final ChallengeCommandService challengeCommandService;
    private final ChallengeQueryService challengeQueryService;

    @Override
    @PostMapping
    public ApiResponse<ChallengeResDTO.Verification> verify(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @Valid @RequestBody ChallengeReqDTO.Verify request
    ) {
        ChallengeResDTO.Verification result =
                challengeCommandService.verify(userDetails.getMemberId(), challengeId, request);
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_VERIFY_SUCCESS, result);
    }

    @Override
    @GetMapping
    public ApiResponse<ChallengeResDTO.Feed> getVerificationFeed(
            @PathVariable Long challengeId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        ChallengeResDTO.Feed result =
                challengeQueryService.getVerificationFeed(challengeId, cursor, size);
        return ApiResponse.onSuccess(ChallengeSuccessCode.VERIFICATION_FEED_FETCH_SUCCESS, result);
    }
}
