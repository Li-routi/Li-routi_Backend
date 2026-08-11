package com.lirouti.domain.charge.controller.docs;

import com.lirouti.domain.charge.dto.request.ChargeReqDTO;
import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Charge", description = "재화 충전·교환 API")
public interface ChargeControllerDocs {

    @Operation(
            summary = "충전 상품 목록 (파란보석 탭)",
            description = """
                    현금으로 사는 재화 묶음이다. 판매 중인 것만 내려간다.

                    `rewardAmount`(유상)와 `bonusAmount`(무상)를 **나눠서** 내려준다. 팝업이
                    `550코인` 과 `+50 보너스` 를 구분해 보여주기 때문이다.
                    """
    )
    ApiResponse<ChargeResDTO.ChargeItems> getChargeProducts();

    @Operation(
            summary = "교환 상품 목록 (주황보석 탭)",
            description = """
                    재화로 사는 재화 묶음이다. **비율이 묶음마다 다르다**(많이 살수록 유리하다).

                    비율은 서버가 갖고 있으므로 클라이언트가 계산하지 않는다.
                    """
    )
    ApiResponse<ChargeResDTO.ExchangeItems> getExchangeProducts();

    @Operation(
            summary = "결제 시작",
            description = """
                    **돈은 아직 오가지 않는다.** 서버가 결제 식별자(`paymentId`)와 금액을 기록하고
                    내려준다. 클라이언트는 이 값으로 포트원 결제창을 연다.

                    응답의 `amount` 는 **화면 표시용**이다 — 검증은 서버가 저장한 값으로 하므로
                    이 값을 고쳐 보내도 통과하지 않는다.

                    **검증·지급은 아직 구현되지 않았다.** 포트원 자격증명을 받은 뒤에 붙인다.

                    - `CHARGE404_1` 없는 상품
                    - `CHARGE409_1` 판매가 종료된 상품
                    """
    )
    ApiResponse<ChargeResDTO.Started> startCharge(ChargeReqDTO.StartCharge request,
                                                  CustomUserDetails userDetails);

    @Operation(
            summary = "재화 교환",
            description = """
                    보유한 재화를 다른 재화로 바꾼다. **결제와 무관하다.**

                    **차감이 먼저다.** 잔액이 모자라면 아무것도 일어나지 않는다.

                    받는 재화는 **무상**으로 들어간다.

                    - `CHARGE404_1` 없는 상품
                    - `CHARGE409_1` 판매가 종료된 상품
                    - `WALLET409_1` 잔액 부족
                    """
    )
    ApiResponse<ChargeResDTO.ExchangeResult> exchange(ChargeReqDTO.Exchange request,
                                                      CustomUserDetails userDetails);
}
