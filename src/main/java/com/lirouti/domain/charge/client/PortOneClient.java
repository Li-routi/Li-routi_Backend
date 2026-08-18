package com.lirouti.domain.charge.client;

import com.lirouti.global.properties.PortOneProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * 포트원 V2 결제 조회.
 *
 * <p><b>이 조회가 결제의 진실이다.</b> 클라이언트가 "결제했다" 고 말하는 것은 근거가 되지
 * 못한다 — 웹훅 payload 도 마찬가지다. 무엇이 왔든 <b>결제 식별자로 여기에 다시 물어본다.</b>
 *
 * <p>인증은 {@code Authorization: PortOne {API Secret}} 이다. <b>Bearer 가 아니고</b>,
 * PG 사에서 받은 시크릿 키를 넣으면 401 이 된다 — 콘솔에서 발급한 V2 API Secret 이어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient {

    private final RestClient restClient;
    private final PortOneProperties properties;

    /**
     * 결제 한 건을 조회한다.
     *
     * @return 포트원이 모르는 결제면 비어 있다. <b>그것도 정상 응답이다</b> — 결제창을 열지
     *         않고 완료를 부른 경우가 그렇다
     */
    public Optional<PortOnePayment> getPayment(String paymentId) {
        try {
            PortOnePayment payment = restClient.get()
                    .uri(properties.getBaseUrl() + "/payments/{paymentId}", paymentId)
                    .header("Authorization", "PortOne " + properties.getApiSecret())
                    .retrieve()
                    .body(PortOnePayment.class);
            return Optional.ofNullable(payment);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.info("포트원이 모르는 결제입니다. paymentId={}", paymentId);
                return Optional.empty();
            }
            // 401 이면 API Secret 이 잘못된 것이다. 이 로그가 몰려 찍히면 키를 확인한다 —
            // PG 사 시크릿을 넣었을 때 정확히 이렇게 된다.
            log.error("포트원 결제 조회에 실패했습니다. status={}, paymentId={}",
                    e.getStatusCode().value(), paymentId);
            throw e;
        }
    }

    /**
     * 조회 응답. <b>쓰는 필드만 받는다</b> — 포트원이 필드를 늘려도 깨지지 않는다.
     *
     * @param id            결제 식별자. 우리가 만든 {@code paymentId} 와 같아야 한다
     * @param status        {@code PAID} 여야 지급한다. {@code PENDING}·{@code VIRTUAL_ACCOUNT_ISSUED}
     *                      는 아직 돈이 들어오지 않은 상태다
     * @param transactionId 포트원이 만든 거래 식별자
     * @param amount        금액. {@code total} 을 우리가 기록한 값과 대조한다
     */
    public record PortOnePayment(
            String id,
            String status,
            String transactionId,
            Amount amount,
            String paidAt
    ) {
        public record Amount(Integer total) {
        }

        public boolean isPaid() {
            return "PAID".equals(status);
        }

        public int totalAmount() {
            return amount == null || amount.total() == null ? -1 : amount.total();
        }
    }
}
