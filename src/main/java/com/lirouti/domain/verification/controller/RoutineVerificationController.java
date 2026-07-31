package com.lirouti.domain.verification.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.verification.controller.docs.RoutineVerificationControllerDocs;
import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.exception.code.success.VerificationSuccessCode;
import com.lirouti.domain.verification.service.RoutineVerificationService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 루틴 인증 진입점.
 *
 * <p>경로를 대상별로 둔다. 하나로 합쳐 타입을 본문에 싣는 방법도 있지만, 화면이 이미
 * 셋으로 갈려 있어(챌린지·그룹·홈) 클라이언트가 무엇을 인증하는지 항상 알고 있다.
 * 합치면 아는 정보를 지웠다가 서버에서 다시 복원하게 되고, 소유권 검증도 셋이 달라
 * 분기가 늘어난다. 도메인은 하나로 묶되 경로만 나눈다.
 */
@RestController
@RequiredArgsConstructor
public class RoutineVerificationController implements RoutineVerificationControllerDocs {

    private final RoutineVerificationService routineVerificationService;

    @Override
    @PostMapping("/api/groups/{groupId}/routines/{routineId}/verifications")
    public ApiResponse<VerificationResDTO.GroupRoutine> verifyGroupRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @PathVariable Long routineId,
            @Valid @RequestBody VerificationReqDTO.Verify request
    ) {
        VerificationResDTO.GroupRoutine result = routineVerificationService.verifyGroupRoutine(
                userDetails.getMemberId(), groupId, routineId, request);
        return ApiResponse.onSuccess(VerificationSuccessCode.GROUP_ROUTINE_VERIFY_SUCCESS, result);
    }

    @Override
    @PostMapping("/api/routines/{routineId}/verifications")
    public ApiResponse<VerificationResDTO.MemberRoutine> verifyMemberRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long routineId,
            @Valid @RequestBody VerificationReqDTO.Verify request
    ) {
        VerificationResDTO.MemberRoutine result = routineVerificationService.verifyMemberRoutine(
                userDetails.getMemberId(), routineId, request);
        return ApiResponse.onSuccess(VerificationSuccessCode.MEMBER_ROUTINE_VERIFY_SUCCESS, result);
    }
}
