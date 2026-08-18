package com.lirouti.domain.verification.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.global.properties.AiReviewProperties;

/**
 * 심사 응답 해석.
 *
 * <p>다른 심사 테스트는 전부 이 클라이언트를 목으로 덮는다. 그래서 <b>여기서 보는 분기들은
 * 다른 어떤 테스트에서도 실행되지 않는다.</b> 특히 안전 거부 분기가 그렇다 — 그것을 못 알아보면
 * "판정 결과 없음 → 해석 실패 → fail-open → 통과"가 되어, 가장 걸러야 할 사진이 가장 확실하게
 * 통과한다. 외부 호출 없이 응답 모양만 만들어 확인한다.
 */
@DisplayName("OpenAI 심사 응답 해석 테스트")
class OpenAiVerificationReviewClientTest {

    private final OpenAiVerificationReviewClient client = new OpenAiVerificationReviewClient(properties());

    private static AiReviewProperties properties() {
        AiReviewProperties properties = new AiReviewProperties();
        properties.setApiKey("sk-test-only-do-not-use");
        properties.setModel("gpt-5.6-luna");
        // 생성자가 read timeout 에 쓴다. 없으면 NPE 라 값이 필요하다.
        properties.setTimeout(Duration.ofSeconds(20));
        return properties;
    }

    /** 응답 한 겹을 만든다. 파트는 순서를 그대로 유지한다 — 순서가 판정을 가르기 때문이다. */
    private static Map<String, Object> response(Object... parts) {
        return Map.of("output", List.of(Map.of(
                "type", "message",
                "content", List.of(parts))));
    }

    private static Map<String, Object> text(String json) {
        return Map.of("type", "output_text", "text", json);
    }

    private static Map<String, Object> refusal(String message) {
        return Map.of("type", "refusal", "refusal", message);
    }

    @Test
    @DisplayName("모델이 거부하면 반려(UNSAFE)다 — 통과로 흘리면 가장 걸러야 할 사진이 통과한다")
    void parse_Refusal_IsRejectedAsUnsafe() {
        VerificationReview review = client.parse(
                response(refusal("I'm sorry, I cannot assist with that request.")), "물 1L 마시기");

        assertThat(review.rejected()).isTrue();
        assertThat(review.rejection()).isEqualTo(ReviewRejection.UNSAFE);
        assertThat(review.shouldHold()).as("거부는 장애가 아니므로 보류가 아니다").isFalse();
    }

    @Test
    @DisplayName("판정 텍스트가 먼저 와도 거부가 이긴다 — 파싱 순서가 뒤집히면 조용히 통과한다")
    void parse_RefusalAfterText_StillRejects() {
        // 이 테스트가 지키는 것은 결과가 아니라 순서다. 텍스트를 먼저 읽고 반환해 버리는
        // 구현으로 바뀌면 거부가 사라지는데, 그 회귀는 다른 어떤 테스트도 잡지 못한다.
        VerificationReview review = client.parse(
                response(text("{\"approved\":true,\"rejection\":\"NONE\",\"reason\":\"좋아요\"}"),
                        refusal("정책상 답할 수 없습니다.")),
                "물 1L 마시기");

        assertThat(review.rejected()).isTrue();
        assertThat(review.rejection()).isEqualTo(ReviewRejection.UNSAFE);
    }

    @Test
    @DisplayName("통과 판정은 그대로 통과다")
    void parse_Approved_Passes() {
        VerificationReview review = client.parse(
                response(text("{\"approved\":true,\"rejection\":\"NONE\",\"reason\":\"물병이 보입니다\"}")),
                "물 1L 마시기");

        assertThat(review.approved()).isTrue();
    }

    @Test
    @DisplayName("불일치 반려는 사유 문장을 그대로 싣는다")
    void parse_Mismatch_CarriesReason() {
        VerificationReview review = client.parse(
                response(text("{\"approved\":false,\"rejection\":\"MISMATCH\",\"reason\":\"커피로 보입니다\"}")),
                "물 1L 마시기");

        assertThat(review.rejection()).isEqualTo(ReviewRejection.MISMATCH);
        assertThat(review.reason()).isEqualTo("커피로 보입니다");
    }

    @Test
    @DisplayName("모르는 반려 종류는 안전한 쪽(UNSAFE)으로 접는다 — 불일치로 접으면 유해 집계가 사라진다")
    void parse_UnknownRejection_FoldsToUnsafe() {
        VerificationReview review = client.parse(
                response(text("{\"approved\":false,\"rejection\":\"WEIRD\",\"reason\":\"알 수 없음\"}")),
                "물 1L 마시기");

        assertThat(review.rejection()).isEqualTo(ReviewRejection.UNSAFE);
    }

    @Test
    @DisplayName("approved 가 없으면 보류다 — 다시 부르면 제대로 올 수 있다")
    void parse_MissingApproved_IsHeld() {
        VerificationReview review = client.parse(
                response(text("{\"rejection\":\"NONE\",\"reason\":\"…\"}")), "물 1L 마시기");

        assertThat(review.shouldHold()).isTrue();
    }

    @Test
    @DisplayName("JSON 이 깨져 있어도 보류다 — 통과로 흘리면 계약이 깨진 동안 심사가 사라진다")
    void parse_BrokenJson_IsHeld() {
        VerificationReview review = client.parse(response(text("{approved:")), "물 1L 마시기");

        assertThat(review.shouldHold()).isTrue();
    }

    @Test
    @DisplayName("응답이 비었거나 output 이 없으면 보류다")
    void parse_EmptyResponse_IsHeld() {
        assertThat(client.parse(null, "물 1L 마시기").shouldHold()).isTrue();
        assertThat(client.parse(Map.of(), "물 1L 마시기").shouldHold()).isTrue();
    }

    @Test
    @DisplayName("심사기가 받지 않는 형식은 부르지 않고 건너뛴다 — 보류로 두면 상한까지 재시도하다 통과한다")
    void review_UnsupportedMimeType_IsSkippedNotHeld() {
        // 네트워크를 타기 전에 걸러지므로 실제 호출이 없다.
        VerificationReview review = client.review("물 1L 마시기", "하루에 물 1L 이상",
                new MediaImage(new byte[] {1, 2, 3}, "image/heic", "etag-test"));

        assertThat(review.approved()).as("판정이 난 것이 아니라 못 한 것이다").isFalse();
        assertThat(review.rejected()).isFalse();
        assertThat(review.shouldHold()).as("다시 불러도 같은 형식이라 보류가 아니다").isFalse();
    }
}
