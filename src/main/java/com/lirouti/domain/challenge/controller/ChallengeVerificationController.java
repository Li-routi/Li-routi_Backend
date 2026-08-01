package com.lirouti.domain.challenge.controller;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 챌린지 인증과 인증 피드. 모든 경로에 로그인이 필요하다.
 * 이 서비스에는 게스트 개념이 없어 공개 경로는 인증·문서·헬스체크뿐이다(#77).
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        // 조회자가 신고한 인증을 빼려면 누가 보는지 알아야 한다(#15). 이 API는 인증이 필요해
        // principal이 null이 아니지만, 서비스는 null이면 필터를 걸지 않도록 되어 있다.
        ChallengeResDTO.Feed result = challengeQueryService
                .getVerificationFeed(challengeId, userDetails.getMemberId(), cursor, size);
        return ApiResponse.onSuccess(ChallengeSuccessCode.VERIFICATION_FEED_FETCH_SUCCESS, result);
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<ChallengeResDTO.MyVerifications> getMyVerifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        // 피드(GET /)와 파라미터를 공유하지 않고 경로를 나눈 것은, 한 엔드포인트가 두 화면을
        // 겸하면 응답 형태와 Swagger 설명이 섞이기 때문이다(#62).
        ChallengeResDTO.MyVerifications result = challengeQueryService
                .getMyVerifications(userDetails.getMemberId(), challengeId, cursor, size);
        return ApiResponse.onSuccess(ChallengeSuccessCode.MY_VERIFICATION_FETCH_SUCCESS, result);
    }

    @Override
    @PostMapping("/{verificationId}/likes")
    public ApiResponse<ChallengeResDTO.Like> like(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId
    ) {
        // 이미 눌러둔 상태여도 성공이다. 좋아요는 토글이라 같은 요청이 두 번 오는 것이 정상이다(#63).
        ChallengeResDTO.Like result =
                challengeCommandService.like(userDetails.getMemberId(), challengeId, verificationId);
        return ApiResponse.onSuccess(ChallengeSuccessCode.VERIFICATION_LIKE_SUCCESS, result);
    }

    @Override
    @DeleteMapping("/{verificationId}/likes")
    public ApiResponse<ChallengeResDTO.Like> unlike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId
    ) {
        ChallengeResDTO.Like result =
                challengeCommandService.unlike(userDetails.getMemberId(), challengeId, verificationId);
        return ApiResponse.onSuccess(ChallengeSuccessCode.VERIFICATION_UNLIKE_SUCCESS, result);
    }

    @Override
    @PostMapping("/{verificationId}/reports")
    public ApiResponse<ChallengeResDTO.Report> report(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId,
            @Valid @RequestBody ChallengeReqDTO.Report request
    ) {
        ChallengeResDTO.Report result = challengeCommandService
                .report(userDetails.getMemberId(), challengeId, verificationId, request);
        return ApiResponse.onSuccess(ChallengeSuccessCode.VERIFICATION_REPORT_SUCCESS, result);
    }

    @Override
    @PatchMapping("/{verificationId}")
    public ApiResponse<ChallengeResDTO.MemoUpdate> updateMemo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId,
            @Valid @RequestBody ChallengeReqDTO.UpdateMemo request
    ) {
        ChallengeResDTO.MemoUpdate result = challengeCommandService
                .updateMemo(userDetails.getMemberId(), challengeId, verificationId, request);
        return ApiResponse.onSuccess(ChallengeSuccessCode.VERIFICATION_MEMO_UPDATE_SUCCESS, result);
    }
}
