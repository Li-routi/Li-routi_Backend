package com.lirouti.domain.wallet.controller.docs;

import com.lirouti.domain.wallet.dto.response.WalletResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Wallet", description = "재화 지갑 API")
public interface WalletControllerDocs {

    @Operation(
            summary = "내 재화 잔액 조회",
            description = """
                    상점 헤더에 표시할 재화 잔액이다.

                    재화 종류마다 한 건씩 **항상 전부** 내려간다. 한 번도 받은 적 없는 재화도
                    `balance: 0` 으로 실리므로, 클라이언트는 "안 온 재화"를 따로 처리하지 않는다.

                    유상(현금으로 산 것)과 무상(보너스·리워드)의 구분은 내리지 않는다. 화면이
                    총합만 쓰기 때문이다.
                    """
    )
    ApiResponse<WalletResDTO.Balances> getMyBalances(CustomUserDetails userDetails);
}
