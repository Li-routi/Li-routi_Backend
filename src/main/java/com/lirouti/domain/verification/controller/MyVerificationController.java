package com.lirouti.domain.verification.controller;

import com.lirouti.domain.verification.controller.docs.MyVerificationControllerDocs;
import com.lirouti.domain.verification.dto.response.MyVerificationResDTO;
import com.lirouti.domain.verification.exception.code.success.VerificationSuccessCode;
import com.lirouti.domain.verification.service.query.MyVerificationQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/verifications")
public class MyVerificationController implements MyVerificationControllerDocs {
    private final MyVerificationQueryService myVerificationQueryService;

    @Override
    @GetMapping
    public ApiResponse<MyVerificationResDTO.DailyFeed> getMyVerifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(TimeUtil.KST);
        MyVerificationResDTO.DailyFeed result =
                myVerificationQueryService.getMyVerifications(userDetails.getMemberId(), targetDate);
        return ApiResponse.onSuccess(VerificationSuccessCode.MY_VERIFICATION_DAILY_FETCH_SUCCESS, result);
    }
}
