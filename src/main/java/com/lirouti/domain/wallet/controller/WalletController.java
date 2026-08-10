package com.lirouti.domain.wallet.controller;

import com.lirouti.domain.wallet.controller.docs.WalletControllerDocs;
import com.lirouti.domain.wallet.dto.response.WalletResDTO;
import com.lirouti.domain.wallet.exception.code.success.WalletSuccessCode;
import com.lirouti.domain.wallet.service.query.WalletQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/wallet")
public class WalletController implements WalletControllerDocs {

    private final WalletQueryService walletQueryService;

    @Override
    @GetMapping
    public ApiResponse<WalletResDTO.Balances> getMyBalances(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WalletResDTO.Balances result = walletQueryService.getBalances(userDetails.getMemberId());
        return ApiResponse.onSuccess(WalletSuccessCode.WALLET_BALANCE_FETCH_SUCCESS, result);
    }
}
