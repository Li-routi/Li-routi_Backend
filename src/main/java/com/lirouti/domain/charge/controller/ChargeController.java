package com.lirouti.domain.charge.controller;

import com.lirouti.domain.charge.controller.docs.ChargeControllerDocs;
import com.lirouti.domain.charge.dto.request.ChargeReqDTO;
import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.exception.code.success.ChargeSuccessCode;
import com.lirouti.domain.charge.service.command.ChargeCommandService;
import com.lirouti.domain.charge.service.query.ChargeQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shop")
public class ChargeController implements ChargeControllerDocs {

    private final ChargeQueryService chargeQueryService;
    private final ChargeCommandService chargeCommandService;

    @Override
    @GetMapping("/charge-products")
    public ApiResponse<ChargeResDTO.ChargeItems> getChargeProducts() {
        return ApiResponse.onSuccess(
                ChargeSuccessCode.CHARGE_PRODUCT_LIST_FETCH_SUCCESS,
                chargeQueryService.getChargeProducts());
    }

    @Override
    @GetMapping("/exchange-products")
    public ApiResponse<ChargeResDTO.ExchangeItems> getExchangeProducts() {
        return ApiResponse.onSuccess(
                ChargeSuccessCode.EXCHANGE_PRODUCT_LIST_FETCH_SUCCESS,
                chargeQueryService.getExchangeProducts());
    }

    @Override
    @PostMapping("/charges")
    public ApiResponse<ChargeResDTO.Started> startCharge(
            @Valid @RequestBody ChargeReqDTO.StartCharge request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ChargeResDTO.Started result =
                chargeCommandService.startCharge(userDetails.getMemberId(), request.productId());
        return ApiResponse.onSuccess(ChargeSuccessCode.CHARGE_START_SUCCESS, result);
    }

    @Override
    @PostMapping("/charges/{paymentId}/complete")
    public ApiResponse<ChargeResDTO.Started> completeCharge(
            @PathVariable String paymentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ChargeResDTO.Started result =
                chargeCommandService.complete(userDetails.getMemberId(), paymentId);
        return ApiResponse.onSuccess(ChargeSuccessCode.CHARGE_COMPLETE_SUCCESS, result);
    }

    /**
     * 포트원이 부른다. <b>인증이 없다</b> — 대신 본문을 믿지 않고 결제 식별자로 다시 조회한다.
     *
     * <p><b>결제 완료 통지에만 반응한다.</b> 포트원은 준비·실패·취소·가상계좌 발급 등을 같은
     * 주소로 보내므로, 나머지는 성공으로 답하고 흘린다 — 실패로 답하면 포트원이 재시도한다.
     */
    @Override
    @PostMapping("/charges/webhook")
    public ApiResponse<Void> webhook(@Valid @RequestBody ChargeReqDTO.Webhook request) {
        if (request.isPaid()) {
            chargeCommandService.handleWebhook(request.data().paymentId());
        }
        return ApiResponse.onSuccess(ChargeSuccessCode.CHARGE_WEBHOOK_SUCCESS, null);
    }

    @Override
    @PostMapping("/exchanges")
    public ApiResponse<ChargeResDTO.ExchangeResult> exchange(
            @Valid @RequestBody ChargeReqDTO.Exchange request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ChargeResDTO.ExchangeResult result =
                chargeCommandService.exchange(
                        userDetails.getMemberId(), request.productId(), request.idempotencyKey());
        return ApiResponse.onSuccess(ChargeSuccessCode.EXCHANGE_SUCCESS, result);
    }
}
