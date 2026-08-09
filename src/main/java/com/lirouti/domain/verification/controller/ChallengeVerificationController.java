package com.lirouti.domain.verification.controller;

import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.controller.docs.ChallengeVerificationControllerDocs;
import com.lirouti.domain.verification.service.ChallengeVerificationService;
import com.lirouti.domain.verification.service.query.ChallengeVerificationQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.lirouti.domain.verification.exception.code.success.ChallengeVerificationSuccessCode;

/**
 * 챌린지 인증과 인증 피드. 모든 경로에 로그인이 필요하다.
 * 이 서비스에는 게스트 개념이 없어 공개 경로는 인증·문서·헬스체크뿐이다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/challenges/{challengeId}/verifications")
public class ChallengeVerificationController implements ChallengeVerificationControllerDocs {
    private final ChallengeVerificationService challengeVerificationService;
    private final ChallengeVerificationQueryService challengeVerificationQueryService;

    @Override
    @PostMapping
    public ApiResponse<ChallengeVerificationResDTO.Verification> verify(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @Valid @RequestBody ChallengeVerificationReqDTO.Verify request
    ) {
        ChallengeVerificationResDTO.Verification result =
                challengeVerificationService.verify(userDetails.getMemberId(), challengeId, request);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.CHALLENGE_VERIFY_SUCCESS, result);
    }

    @Override
    @GetMapping
    public ApiResponse<ChallengeVerificationResDTO.Feed> getVerificationFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        // 조회자가 신고한 인증을 빼려면 누가 보는지 알아야 한다. 이 API는 인증이 필요해
        // principal이 null이 아니지만, 서비스는 null이면 필터를 걸지 않도록 되어 있다.
        ChallengeVerificationResDTO.Feed result = challengeVerificationQueryService
                .getVerificationFeed(challengeId, userDetails.getMemberId(), cursor, size);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.VERIFICATION_FEED_FETCH_SUCCESS, result);
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<ChallengeVerificationResDTO.MyVerifications> getMyVerifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) ReviewStatus status
    ) {
        // 피드(GET /)와 파라미터를 공유하지 않고 경로를 나눈 것은, 한 엔드포인트가 두 화면을
        // 겸하면 응답 형태와 Swagger 설명이 섞이기 때문이다.
        ChallengeVerificationResDTO.MyVerifications result = challengeVerificationQueryService
                .getMyVerifications(userDetails.getMemberId(), challengeId, cursor, size, status);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.MY_VERIFICATION_FETCH_SUCCESS, result);
    }

    @Override
    @PostMapping("/{verificationId}/likes")
    public ApiResponse<ChallengeVerificationResDTO.Like> like(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId
    ) {
        // 이미 눌러둔 상태여도 성공이다. 좋아요는 토글이라 같은 요청이 두 번 오는 것이 정상이다.
        ChallengeVerificationResDTO.Like result =
                challengeVerificationService.like(userDetails.getMemberId(), challengeId, verificationId);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.VERIFICATION_LIKE_SUCCESS, result);
    }

    @Override
    @DeleteMapping("/{verificationId}/likes")
    public ApiResponse<ChallengeVerificationResDTO.Like> unlike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId
    ) {
        ChallengeVerificationResDTO.Like result =
                challengeVerificationService.unlike(userDetails.getMemberId(), challengeId, verificationId);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.VERIFICATION_UNLIKE_SUCCESS, result);
    }

    @Override
    @PostMapping("/{verificationId}/reports")
    public ApiResponse<ChallengeVerificationResDTO.Report> report(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId,
            @Valid @RequestBody ChallengeVerificationReqDTO.Report request
    ) {
        ChallengeVerificationResDTO.Report result = challengeVerificationService
                .report(userDetails.getMemberId(), challengeId, verificationId, request);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.VERIFICATION_REPORT_SUCCESS, result);
    }

    @Override
    @PatchMapping("/{verificationId}")
    public ApiResponse<ChallengeVerificationResDTO.MemoUpdate> updateMemo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId,
            @Valid @RequestBody ChallengeVerificationReqDTO.UpdateMemo request
    ) {
        ChallengeVerificationResDTO.MemoUpdate result = challengeVerificationService
                .updateMemo(userDetails.getMemberId(), challengeId, verificationId, request);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.VERIFICATION_MEMO_UPDATE_SUCCESS, result);
    }

    @Override
    @DeleteMapping("/{verificationId}")
    public ApiResponse<ChallengeVerificationResDTO.Deletion> deleteVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId,
            @PathVariable Long verificationId
    ) {
        ChallengeVerificationResDTO.Deletion result = challengeVerificationService
                .deleteVerification(userDetails.getMemberId(), challengeId, verificationId);
        return ApiResponse.onSuccess(ChallengeVerificationSuccessCode.VERIFICATION_DELETE_SUCCESS, result);
    }
}
