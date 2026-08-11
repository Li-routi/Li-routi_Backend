package com.lirouti.domain.charge;

import com.lirouti.domain.charge.client.PortOneClient;
import com.lirouti.domain.charge.entity.ChargeProduct;
import com.lirouti.domain.charge.enums.ChargePaymentStatus;
import com.lirouti.domain.charge.repository.ChargePaymentRepository;
import com.lirouti.domain.charge.repository.ChargeProductRepository;
import com.lirouti.domain.charge.service.command.ChargeCommandService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 포트원이 실제로 보내는 본문을 받아 처리하는가.
 *
 * <p><b>이 경로는 인증이 없다.</b> 포트원이 부르는 자리라 JWT 를 붙일 수 없고, 대신 본문을
 * 믿지 않는 것으로 대신한다.
 *
 * <p>처음에는 결제 식별자를 <b>최상위</b>에서 읽었다. 포트원 V2 는 {@code data} 안에 담으므로
 * 실제 웹훅이 오면 {@code null} 이 되어 <b>아무 결제도 처리되지 않았을 것</b>이다 — 그런데
 * 서비스 단위 테스트만으로는 드러나지 않는다. 그래서 실제 JSON 으로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("포트원 웹훅 수신")
class ChargeWebhookTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ChargeCommandService chargeCommandService;
    @Autowired private ChargePaymentRepository chargePaymentRepository;
    @Autowired private ChargeProductRepository chargeProductRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberWalletRepository memberWalletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @MockitoBean private PortOneClient portOneClient;

    private Long memberId;
    private Long productId;
    private String paymentId;

    @BeforeEach
    void setUp() {
        String tag = "wh-" + System.nanoTime();
        memberId = memberRepository.save(Member.builder()
                .email(tag + "@ex.com").nickname(tag)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(tag).build()).getId();
        productId = chargeProductRepository.save(ChargeProduct.builder()
                .rewardCurrency(Currency.GEM).rewardAmount(500).bonusAmount(50)
                .priceKrw(5500).popular(false).sortOrder(1).active(true).build()).getId();
        paymentId = chargeCommandService.startCharge(memberId, productId).paymentId();
    }

    @AfterEach
    void tearDown() {
        chargePaymentRepository.deleteAll(chargePaymentRepository.findAll().stream()
                .filter(p -> p.getMember().getId().equals(memberId)).toList());
        walletTransactionRepository.deleteAll(walletTransactionRepository.findAll().stream()
                .filter(t -> t.getMember().getId().equals(memberId)).toList());
        memberWalletRepository.findAllByMemberId(memberId).forEach(memberWalletRepository::delete);
        chargeProductRepository.deleteById(productId);
        memberRepository.deleteById(memberId);
    }

    /** 포트원 V2 가 실제로 보내는 모양. 결제 식별자가 {@code data} 안에 있다. */
    private String body(String type) {
        return """
                {
                  "type": "%s",
                  "timestamp": "2026-08-12T00:00:00Z",
                  "data": { "storeId": "store-test-only", "paymentId": "%s", "transactionId": "tx-1" }
                }
                """.formatted(type, paymentId);
    }

    private void portOnePaid() {
        when(portOneClient.getPayment(any())).thenReturn(Optional.of(
                new PortOneClient.PortOnePayment(paymentId, "PAID", "tx-1",
                        new PortOneClient.PortOnePayment.Amount(5500), "2026-08-12T00:00:00Z")));
    }

    @Test
    @DisplayName("결제 완료 통지를 받으면 인증 없이도 지급된다")
    void webhook_PaidGrants() throws Exception {
        portOnePaid();

        mockMvc.perform(post("/api/shop/charges/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Transaction.Paid")))
                .andExpect(status().isOk());

        assertThat(chargePaymentRepository.findByPaymentIdForRead(paymentId).orElseThrow()
                .getStatus()).isEqualTo(ChargePaymentStatus.PAID);
    }

    @Test
    @DisplayName("결제 완료가 아닌 통지는 흘린다 — 실패로 답하면 포트원이 재시도한다")
    void webhook_IgnoresOtherEvents() throws Exception {
        mockMvc.perform(post("/api/shop/charges/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Transaction.VirtualAccountIssued")))
                .andExpect(status().isOk());

        verify(portOneClient, never()).getPayment(any());
        assertThat(chargePaymentRepository.findByPaymentIdForRead(paymentId).orElseThrow()
                .getStatus()).isEqualTo(ChargePaymentStatus.READY);
    }

    @Test
    @DisplayName("본문이 계약과 다르면 거절한다 — 최상위 paymentId 는 받지 않는다")
    void webhook_RejectsWrongShape() throws Exception {
        mockMvc.perform(post("/api/shop/charges/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Transaction.Paid\",\"paymentId\":\"" + paymentId + "\"}"))
                .andExpect(status().isBadRequest());

        verify(portOneClient, never()).getPayment(any());
    }

    @Test
    @DisplayName("모르는 결제 식별자는 포트원에 묻지 않는다 — 공개 주소라 아무나 보낼 수 있다")
    void webhook_DoesNotCallPortOneForUnknownPayment() throws Exception {
        // 이 주소는 인증이 없다. 여기서 끊지 않으면 아무 값이나 보내는 것만으로
        // 우리가 포트원 API 를 대신 두들기게 된다.
        mockMvc.perform(post("/api/shop/charges/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"charge_이런건없다\"}}"))
                .andExpect(status().isOk());

        verify(portOneClient, never()).getPayment(any());
    }
}
