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
 * 장애·타임아웃·응답 형식 이상은 전부 {@link VerificationReview#undecided()} 로 돌려주고,
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
            // 여기서 던지면 fail-closed 가 된다. 어떤 실패든 "심사 못 함"으로 흘린다.
            log.warn("AI 심사를 받지 못해 통과시킵니다. challenge={}", challengeName, e);
            return VerificationReview.undecided();
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
                                        "description", "사진이 챌린지 의도에 맞으면 true"),
                                "reason", Map.of(
                                        "type", "string",
                                        "description", "판단 근거를 사용자에게 보여줄 한국어 한 문장으로. "
                                                + "반려일 때 특히 구체적으로 적는다.")),
                        "required", List.of("approved", "reason")));
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
                아래 사진이 다음 챌린지를 실제로 수행한 인증 사진으로 볼 수 있는지 판단해 주세요.

                챌린지: %s

                판단 기준
                - 사진의 내용이 그 챌린지가 요구하는 행동·대상과 맞는지만 봅니다.
                - 화질·구도·조명은 평가하지 않습니다.
                - 애매하면 통과시킵니다. 명백히 무관하거나 부적절할 때만 반려하세요.
                - 사람 얼굴이 나와도 그 자체는 문제가 아닙니다.

                submit_review 도구로만 답하세요.
                """.formatted(intent);
    }

    @SuppressWarnings("unchecked")
    private VerificationReview parse(Map<String, Object> response, String challengeName) {
        if (response == null) {
            return undecidedWithLog("응답이 비어 있습니다", challengeName);
        }
        Object content = response.get("content");
        if (!(content instanceof List<?> blocks)) {
            return undecidedWithLog("content 가 없습니다", challengeName);
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
            Object reason = ((Map<String, Object>) input).get("reason");
            return VerificationReview.reject(trimReason(reason));
        }
        // 도구를 강제했는데도 안 왔다면 모델이나 API 쪽이 바뀐 것이다. 막지 않고 드러낸다.
        return undecidedWithLog("도구 호출 결과가 없습니다", challengeName);
    }

    private String trimReason(Object reason) {
        if (!(reason instanceof String text) || text.isBlank()) {
            return "챌린지 내용과 맞지 않는 사진입니다.";
        }
        return text.length() <= MAX_REASON_LENGTH ? text : text.substring(0, MAX_REASON_LENGTH);
    }

    private VerificationReview undecidedWithLog(String why, String challengeName) {
        log.warn("AI 심사 응답을 해석하지 못해 통과시킵니다. 이유={}, challenge={}", why, challengeName);
        return VerificationReview.undecided();
    }
}
