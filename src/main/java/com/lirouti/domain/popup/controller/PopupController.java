package com.lirouti.domain.popup.controller;

import com.lirouti.domain.popup.controller.docs.PopupControllerDocs;
import com.lirouti.domain.popup.dto.request.PopupReqDTO;
import com.lirouti.domain.popup.dto.response.PopupResDTO;
import com.lirouti.domain.popup.exception.code.success.PopupSuccessCode;
import com.lirouti.domain.popup.service.command.PopupCommandService;
import com.lirouti.domain.popup.service.query.PopupQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/popups")
public class PopupController implements PopupControllerDocs {

    private final PopupQueryService popupQueryService;
    private final PopupCommandService popupCommandService;

    @Override
    @GetMapping("/pending")
    public ApiResponse<PopupResDTO.Popups> getPending(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PopupResDTO.Popups result = popupQueryService.getPending(userDetails.getMemberId());
        return ApiResponse.onSuccess(PopupSuccessCode.POPUP_PENDING_FETCH_SUCCESS, result);
    }

    @Override
    @PostMapping("/ack")
    public ApiResponse<Void> ack(
            @Valid @RequestBody PopupReqDTO.Ack request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        popupCommandService.ack(userDetails.getMemberId(), request.popupIds());
        return ApiResponse.onSuccess(PopupSuccessCode.POPUP_ACK_SUCCESS, null);
    }
}
