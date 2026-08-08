package com.lirouti.domain.challenge.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.global.properties.AiReviewProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 인증 사진이 챌린지 의도에 맞는지 Claude 에게 묻는다.
 *
 * <h2>답을 못 받으면 통과시킨다</h2>
 * 장애·타임아웃·응답 형식 이상은 전부 {@link VerificationReview#transientFailure} 로 돌려주고,
 * 호출부가 그것을 통과로 다룬다(fail-open). 외부 API 하나가 인증 기능 전체를 멈추게 두지
 * 않는다는 결정이다. 부적절한 사진은 신고 누적 숨김이라는 다른 방어선이 있다.
 *
 * <p>그래서 이 클래스는 <b>예외를 밖으로 던지지 않는다.</b> 던지면 그 순간 fail-closed 가 된다.
 *
 * <h2>판정은 도구 호출로 받는다</h2>
 * 자유 텍스트를 파싱하면 형식이 흔들려 "통과인지 반려인지"가 불안정해진다. 스키마를 고정한
 * 도구를 하나 주고 그것만 쓰게 해서 항상 같은 모양으로 받는다.
 */
@Slf4j
@Component
public class AnthropicVerificationReviewClient {
    private static final String MESSAGES_URI = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String REVIEW_TOOL = "submit_review";
    private static final int MAX_TOKENS = 300;
    private static final String REFUSAL_STOP_REASON = "refusal";

    /** 반려 사유가 길면 화면이 깨진다. 한 문장 정도로 자른다. */
    private static final int MAX_REASON_LENGTH = 200;

    private final RestClient restClient;
    private final AiReviewProperties properties;

    public AnthropicVerificationReviewClient(AiReviewProperties properties) {
        this.properties = properties;
        // 공용 RestClient 는 소셜 로그인 기준으로 read timeout 이 짧다. 비전 판정은 그보다
        // 오래 걸리므로 이 용도의 클라이언트를 따로 만든다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * @param challengeName        챌린지 이름. 판정의 주된 근거다
     * @param challengeDescription 챌린지 설명. 없을 수 있다
     * @param image                심사할 사진
     */
    public VerificationReview review(String challengeName, String challengeDescription, MediaImage image) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(MESSAGES_URI)
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(challengeName, challengeDescription, image))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
            return parse(response, challengeName);
        } catch (RuntimeException e) {
            // 여기서 던지면 fail-closed 가 된다. 어떤 실패든 "답을 못 받았다"로 흘린다.
            // 호출·타임아웃 실패는 다시 하면 될 수 있으므로 보류 대상이다.
            log.warn("AI 심사를 받지 못했습니다. challenge={}", challengeName, e);
            return VerificationReview.transientFailure(e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> requestBody(String name, String description, MediaImage image) {
        String encoded = Base64.getEncoder().encodeToString(image.bytes());
        return Map.of(
                "model", properties.getModel(),
                "max_tokens", MAX_TOKENS,
                "tools", List.of(reviewTool()),
                // 도구를 반드시 쓰게 강제한다. 안 그러면 설명 문장만 돌아올 수 있다.
                "tool_choice", Map.of("type", "tool", "name", REVIEW_TOOL),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of(
                                        "type", "base64",
                                        "media_type", image.mimeType(),
                                        "data", encoded)),
                                Map.of("type", "text", "text", prompt(name, description))))));
    }

    private Map<String, Object> reviewTool() {
        return Map.of(
                "name", REVIEW_TOOL,
                "description", "인증 사진 심사 결과를 제출한다.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "approved", Map.of(
                                        "type", "boolean",
                                        "description", "챌린지에 맞고 공개해도 문제없는 사진이면 true"),
                                "rejection", Map.of(
                                        "type", "string",
                                        "enum", List.of("MISMATCH", "UNSAFE", "NONE"),
                                        "description", "MISMATCH=챌린지 내용과 맞지 않음, "
                                                + "UNSAFE=선정적·폭력적이거나 타인의 개인정보가 드러남, "
                                                + "NONE=통과(approved=true 일 때)"),
                                "reason", Map.of(
                                        "type", "string",
                                        "description", "판단 근거를 한국어 한 문장으로. "
                                                + "반려일 때 특히 구체적으로 적는다.")),
                        // rejection 을 필수로 둔다. 빠뜨리면 반려 종류를 알 수 없고, 그때
                        // 안전한 쪽(UNSAFE)으로 접으면 정상 사진이 "공개 불가"로 잘못 안내된다.
                        "required", List.of("approved", "rejection", "reason")));
    }

    /**
     * 판정 기준을 챌린지 이름·설명으로 둔다.
     *
     * 카테고리(운동·식습관 등)로는 "우유 마시기"와 "물 마시기"를 구분하지 못한다.
     * 애매하면 통과시키게 한 것은 의도적이다 — 정상 인증을 반려하는 쪽이 사용자에게 더 나쁘고,
     * 부적절한 사진은 신고로도 걸러진다.
     */
    private String prompt(String name, String description) {
        String intent = (description == null || description.isBlank())
                ? name
                : name + " (" + description + ")";
        return """
                아래 사진을 두 가지 기준으로 심사해 주세요. 이 사진은 통과하면 다른 사용자들이
                보는 공개 피드에 그대로 올라갑니다.

                챌린지: %s

                기준 1 — 챌린지와 맞는가 (반려 시 rejection=MISMATCH)
                - 사진의 내용이 그 챌린지가 요구하는 행동·대상과 맞는지 봅니다.
                - 화질·구도·조명은 평가하지 않습니다. 사람 얼굴이 나와도 그 자체는 문제가 아닙니다.
                - 애매하면 통과시킵니다. 명백히 무관할 때만 반려하세요.

                기준 2 — 공개해도 되는가 (반려 시 rejection=UNSAFE)
                - 선정적이거나 노출이 과한 사진
                - 폭력적이거나 혐오감을 주는 사진
                - 타인의 신분증·연락처·주소 등 개인정보가 알아볼 수 있게 드러난 사진
                - 이 기준은 기준 1과 반대로, **애매하면 반려**하세요. 공개 피드라 되돌릴 수 없습니다.
                - 챌린지와 잘 맞더라도 이 기준에 걸리면 반려입니다.

                두 기준 모두 통과할 때만 approved=true 입니다.
                submit_review 도구로만 답하세요.
                """.formatted(intent);
    }

    @SuppressWarnings("unchecked")
    private VerificationReview parse(Map<String, Object> response, String challengeName) {
        if (response == null) {
            return unparsable("응답이 비어 있습니다", challengeName);
        }
        // 모델이 안전상 응답을 거부한 경우다. 이것을 "심사 못 함"으로 흘리면 통과가 되는데,
        // 그러면 가장 걸러야 할 사진이 가장 확실하게 통과한다. 거부는 장애가 아니라 판단이다.
        if (REFUSAL_STOP_REASON.equals(response.get("stop_reason"))) {
            log.warn("모델이 안전상 심사를 거부해 반려합니다. challenge={}", challengeName);
            return VerificationReview.reject(ReviewRejection.UNSAFE,
                    "공개하기 어려운 사진으로 판단되었습니다.");
        }
        Object content = response.get("content");
        if (!(content instanceof List<?> blocks)) {
            return unparsable("content 가 없습니다", challengeName);
        }
        for (Object block : blocks) {
            if (!(block instanceof Map<?, ?> map) || !"tool_use".equals(map.get("type"))) {
                continue;
            }
            if (!(map.get("input") instanceof Map<?, ?> input)) {
                continue;
            }
            Object approved = ((Map<String, Object>) input).get("approved");
            if (!(approved instanceof Boolean decision)) {
                continue;
            }
            if (decision) {
                return VerificationReview.pass();
            }
            Map<String, Object> values = (Map<String, Object>) input;
            Object rejection = values.get("rejection");
            if (!ReviewRejection.isKnown(rejection)) {
                // 스키마로 강제했는데도 왔다면 모델이나 API 계약이 바뀐 것이다.
                // 조용히 UNSAFE 로 접으면 정상 사진이 "공개 불가"로 안내되므로 드러낸다.
                log.warn("반려 종류를 알 수 없어 안전한 쪽으로 처리합니다. challenge={}, 값={}",
                        challengeName, rejection);
            }
            return VerificationReview.reject(
                    ReviewRejection.from(rejection),
                    trimReason(values.get("reason")));
        }
        // 도구를 강제했는데도 안 왔다면 모델이나 API 쪽이 바뀐 것이다. 막지 않고 드러낸다.
        return unparsable("도구 호출 결과가 없습니다", challengeName);
    }

    private String trimReason(Object reason) {
        if (!(reason instanceof String text) || text.isBlank()) {
            return "챌린지 내용과 맞지 않는 사진입니다.";
        }
        return text.length() <= MAX_REASON_LENGTH ? text : text.substring(0, MAX_REASON_LENGTH);
    }

    /**
     * 응답을 해석하지 못했다. <b>보류 대상으로 다룬다</b> — 모델 응답은 같은 입력에도 달라질 수
     * 있어 다시 부르면 제대로 올 수 있다. 계약이 아예 바뀐 것이라면 상한까지 시도한 뒤 통과한다.
     */
    private VerificationReview unparsable(String why, String challengeName) {
        log.warn("AI 심사 응답을 해석하지 못했습니다. 이유={}, challenge={}", why, challengeName);
        return VerificationReview.transientFailure(why);
    }
}
