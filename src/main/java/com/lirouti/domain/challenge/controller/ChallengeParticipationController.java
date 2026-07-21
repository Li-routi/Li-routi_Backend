package com.lirouti.domain.challenge.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.challenge.controller.docs.ChallengeParticipationControllerDocs;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.exception.code.success.ChallengeSuccessCode;
import com.lirouti.domain.challenge.service.command.ChallengeCommandService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/challenges/{challengeId}/participation")
public class ChallengeParticipationController implements ChallengeParticipationControllerDocs {
    private final ChallengeCommandService challengeCommandService;

    @Override
    @PostMapping
    public ApiResponse<ChallengeResDTO.Participation> participate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId
    ) {
        ChallengeResDTO.Participation result =
                challengeCommandService.participate(userDetails.getMemberId(), challengeId);
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_PARTICIPATE_SUCCESS, result);
    }

    @Override
    @DeleteMapping
    public ApiResponse<ChallengeResDTO.Participation> leave(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long challengeId
    ) {
        ChallengeResDTO.Participation result =
                challengeCommandService.leave(userDetails.getMemberId(), challengeId);
        return ApiResponse.onSuccess(ChallengeSuccessCode.CHALLENGE_LEAVE_SUCCESS, result);
    }
}
