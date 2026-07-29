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
        // 인증이 필요한 경로라 principal은 항상 있다(#77). null 분기를 두지 않는 것은 의도다 —
        // 이 서비스에는 게스트 개념이 없으므로, 비로그인 대비 코드를 남기면 없는 개념을 코드가
        // 다시 만들어 낸다. 서비스 계층은 memberId == null을 여전히 견디게 두었다(그쪽 주석 참고).
        ChallengeResDTO.Detail result =
                challengeQueryService.getChallenge(challengeId, userDetails.getMemberId());
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_DETAIL_FETCH_SUCCESS, result);
    }
}
