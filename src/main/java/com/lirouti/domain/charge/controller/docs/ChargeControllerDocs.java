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
            summary = "결제 검증·지급",
            description = """
                    결제창에서 결제를 마친 뒤 **클라이언트가 부른다.**

                    서버가 **포트원에 다시 물어본다.** 요청에 담긴 값이 아니라 포트원의 답으로
                    판단하므로, 클라이언트가 무엇을 보내도 결과가 바뀌지 않는다.

                    확인하는 것:
                    - **인증된 회원의 결제인가** — 남의 결제 식별자로는 부를 수 없다
                    - 포트원이 말하는 상태가 `PAID` 인가
                    - 금액이 **결제 시작 때 기록한 값과 정확히 같은가**

                    지급은 **결제 시작 때 굳혀 둔 값**으로 한다 — 그 사이 상품이 바뀌어도 영향받지
                    않는다.

                    **이미 지급된 결제면 조용히 성공으로 답한다.** 이 요청과 웹훅이 둘 다 오는
                    것이 정상이다.

                    - `CHARGE404_2` 없는 결제이거나 내 결제가 아님
                    - `CHARGE409_2` 금액 불일치
                    - `CHARGE409_3` 아직 완료되지 않은 결제
                    """
    )
    ApiResponse<ChargeResDTO.Started> completeCharge(String paymentId,
                                                     CustomUserDetails userDetails);

    @Operation(
            summary = "포트원 웹훅 (서버 간 호출)",
            description = """
                    **포트원이 부른다. 인증이 없다.**

                    결제 검증은 클라이언트가 부르는데, 결제 직후 앱이 죽거나 네트워크가 끊기면
                    **돈은 나갔는데 재화가 없는** 상태가 남는다. 이 웹훅이 그것을 메운다.

                    **본문을 믿지 않는다.** 주소가 공개되어 있어 아무나 위조한 본문을 보낼 수
                    있으므로, 결제 식별자만 꺼내 **포트원에 다시 물어보고** 그 답으로만 지급한다.
                    포트원이 모르는 결제면 아무 일도 일어나지 않는다.

                    검증과 지급은 위 API 와 **같은 길**을 쓴다. 둘 중 먼저 온 쪽이 지급하고
                    나중 것은 조용히 끝난다.
                    """
    )
    ApiResponse<Void> webhook(ChargeReqDTO.Webhook request);

    @Operation(
            summary = "재화 교환",
            description = """
                    보유한 재화를 다른 재화로 바꾼다. **결제와 무관하다.**

                    **차감이 먼저다.** 잔액이 모자라면 아무것도 일어나지 않는다.

                    받는 재화는 **무상**으로 들어간다.

                    **`idempotencyKey` 는 클라이언트가 만든다.** 응답을 못 받아 다시 보낼
                    때는 **같은 키**를, 한 번 더 교환할 때는 **새 키**를 쓴다.

                    - 같은 키로 다시 오면 **다시 차감하지 않고** 먼저 처리한 결과를 돌려준다
                    - 서버가 키를 만들면 재시도가 두 번 차감된다
                    - 상품 id 로 키를 만들면 두 번째 교환이 조용히 건너뛰어진다

                    - `CHARGE404_1` 없는 상품
                    - `CHARGE409_1` 판매가 종료된 상품
                    - `WALLET409_1` 잔액 부족
                    """
    )
    ApiResponse<ChargeResDTO.ExchangeResult> exchange(ChargeReqDTO.Exchange request,
                                                      CustomUserDetails userDetails);
}
