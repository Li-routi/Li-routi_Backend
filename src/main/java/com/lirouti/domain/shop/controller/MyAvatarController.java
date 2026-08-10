package com.lirouti.domain.shop.controller;

import com.lirouti.domain.shop.controller.docs.MyAvatarControllerDocs;
import com.lirouti.domain.shop.dto.request.ShopReqDTO;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.exception.code.success.ShopSuccessCode;
import com.lirouti.domain.shop.service.command.ShopCommandService;
import com.lirouti.domain.shop.service.query.ShopQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/avatar")
public class MyAvatarController implements MyAvatarControllerDocs {

    private final ShopQueryService shopQueryService;
    private final ShopCommandService shopCommandService;

    @Override
    @GetMapping
    public ApiResponse<ShopResDTO.Avatar> getMyAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopResDTO.Avatar result = shopQueryService.getMyAvatar(userDetails.getMemberId());
        return ApiResponse.onSuccess(ShopSuccessCode.AVATAR_FETCH_SUCCESS, result);
    }

    @Override
    @PutMapping
    public ApiResponse<ShopResDTO.Avatar> equip(
            @Valid @RequestBody ShopReqDTO.EquipAvatar request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopResDTO.Avatar result =
                shopCommandService.equip(userDetails.getMemberId(), request.itemIds());
        return ApiResponse.onSuccess(ShopSuccessCode.AVATAR_EQUIP_SUCCESS, result);
    }
}
