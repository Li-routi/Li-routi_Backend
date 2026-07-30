package com.lirouti.domain.home.controller;

import com.lirouti.domain.home.controller.docs.HomeControllerDocs;
import com.lirouti.domain.home.dto.response.HomeResDTO;
import com.lirouti.domain.home.service.query.HomeQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.apiPayload.code.GeneralSuccessCode;
import com.lirouti.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home")
public class HomeController implements HomeControllerDocs {

    private final HomeQueryService homeQueryService;

    @Override
    @GetMapping
    public ApiResponse<HomeResDTO.MainSummary> getHomeSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        HomeResDTO.MainSummary response = homeQueryService.getHomeSummary(userDetails.getMemberId());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
