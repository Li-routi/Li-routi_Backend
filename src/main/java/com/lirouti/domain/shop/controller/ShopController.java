package com.lirouti.domain.shop.controller;

import com.lirouti.domain.shop.controller.docs.ShopControllerDocs;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.exception.code.success.ShopSuccessCode;
import com.lirouti.domain.shop.service.command.ShopCommandService;
import com.lirouti.domain.shop.service.query.ShopQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shop/items")
public class ShopController implements ShopControllerDocs {

    private final ShopQueryService shopQueryService;
    private final ShopCommandService shopCommandService;

    @Override
    @GetMapping
    public ApiResponse<ShopResDTO.Items> getItems(
            @RequestParam(required = false) AvatarSlot slot,
            @RequestParam(defaultValue = "false") boolean ownedOnly,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopResDTO.Items result =
                shopQueryService.getItems(userDetails.getMemberId(), slot, ownedOnly);
        return ApiResponse.onSuccess(ShopSuccessCode.SHOP_ITEM_LIST_FETCH_SUCCESS, result);
    }

    @Override
    @PostMapping("/{itemId}/purchase")
    public ApiResponse<ShopResDTO.Avatar> purchase(
            @PathVariable Long itemId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ShopResDTO.Avatar result = shopCommandService.purchase(userDetails.getMemberId(), itemId);
        return ApiResponse.onSuccess(ShopSuccessCode.SHOP_ITEM_PURCHASE_SUCCESS, result);
    }
}
