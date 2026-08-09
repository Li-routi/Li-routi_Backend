package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.controller.docs.GroupInteractionControllerDocs;
import com.lirouti.domain.group.dto.response.GroupInteractionResDTO;
import com.lirouti.domain.group.service.command.GroupInteractionCommandService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.apiPayload.code.GeneralSuccessCode;
import com.lirouti.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Android 그룹 화면의 아쉬워요와 찌르기 상호작용 API다. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/groups/{groupId}")
public class GroupInteractionController implements GroupInteractionControllerDocs {
    private final GroupInteractionCommandService service;

    @Override
    @PostMapping("/verifications/{verificationId}/disappointments")
    public ApiResponse<GroupInteractionResDTO.Disappointment> disappoint(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long groupId,
            @PathVariable Long verificationId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                service.disappoint(user.getMemberId(), groupId, verificationId));
    }
    @Override
    @DeleteMapping("/verifications/{verificationId}/disappointments")
    public ApiResponse<GroupInteractionResDTO.Disappointment> undisappoint(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long groupId,
            @PathVariable Long verificationId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                service.undisappoint(user.getMemberId(), groupId, verificationId));
    }
    @Override
    @PostMapping("/members/{recipientId}/poke")
    public ApiResponse<GroupInteractionResDTO.Poke> poke(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long groupId,
            @PathVariable Long recipientId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                service.poke(user.getMemberId(), groupId, recipientId));
    }
}
