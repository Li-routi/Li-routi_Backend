package com.lirouti.domain.shop.controller;

import com.lirouti.domain.shop.controller.docs.ShopCategoryControllerDocs;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.exception.code.success.ShopSuccessCode;
import com.lirouti.domain.shop.service.query.ShopQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shop/categories")
public class ShopCategoryController implements ShopCategoryControllerDocs {

    private final ShopQueryService shopQueryService;

    @Override
    @GetMapping
    public ApiResponse<ShopResDTO.Categories> getCategories() {
        ShopResDTO.Categories result = shopQueryService.getCategories();
        return ApiResponse.onSuccess(ShopSuccessCode.SHOP_CATEGORY_LIST_FETCH_SUCCESS, result);
    }
}
