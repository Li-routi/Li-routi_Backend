package com.lirouti.domain.charge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ChargeReqDTO {

    private ChargeReqDTO() {
    }

    /** 결제 시작. 상품 id 하나면 된다 — 금액은 서버가 갖고 있다. */
    public record StartCharge(
            @NotNull(message = "상품 id 는 필수입니다.")
            Long productId
    ) {
    }

    /**
     * 포트원 웹훅.
     *
     * <p><b>여기 담긴 값을 믿지 않는다.</b> 웹훅 주소는 공개되어 있어 아무나 위조한 본문을
     * 보낼 수 있다. 결제 식별자만 꺼내 <b>포트원에 다시 물어보고</b>, 그 답으로만 지급한다.
     * 그래서 금액이나 상태를 받지 않는다 — 받아도 쓰지 않을 값이다.
     */
    public record Webhook(
            @NotBlank(message = "결제 식별자는 필수입니다.")
            String paymentId
    ) {
    }

    /**
     * 재화 교환. 비율은 상품이 갖는다.
     *
     * <p><b>멱등 키를 클라이언트가 만든다.</b> 서버가 요청마다 새로 만들면 응답이 유실돼
     * 재시도했을 때 <b>두 번 차감되고 두 번 지급된다.</b> 반대로 상품 id 로 만들면 같은 묶음을
     * 두 번째 살 때 조용히 건너뛰어진다 — 교환은 구매와 달리 반복할 수 있다.
     *
     * <p>그래서 <b>"이 사용자 동작 하나"를 가리키는 값</b>이 필요하고, 그것은 클라이언트만
     * 안다. 재시도는 같은 키로, 새 교환은 새 키로 보낸다.
     */
    public record Exchange(
            @NotNull(message = "상품 id 는 필수입니다.")
            Long productId,

            @NotBlank(message = "멱등 키는 필수입니다.")
            @Size(max = 64, message = "멱등 키는 64자를 넘을 수 없습니다.")
            String idempotencyKey
    ) {
    }
}
