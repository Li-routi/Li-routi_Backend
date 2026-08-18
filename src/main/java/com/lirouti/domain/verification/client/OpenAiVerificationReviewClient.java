package com.lirouti.domain.verification.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.global.properties.AiReviewProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 인증 사진이 챌린지 의도에 맞는지 OpenAI 에게 묻는다.
 *
 * <h2>답을 못 받으면 통과시킨다</h2>
 * 장애·타임아웃·응답 형식 이상은 전부 {@link VerificationReview#transientFailure} 로 돌려주고,
 * 호출부가 그것을 통과로 다룬다(fail-open). 외부 API 하나가 인증 기능 전체를 멈추게 두지
 * 않는다는 결정이다. 부적절한 사진은 신고 누적 숨김이라는 다른 방어선이 있다.
 *
 * <p>그래서 이 클래스는 <b>예외를 밖으로 던지지 않는다.</b> 던지면 그 순간 fail-closed 가 된다.
 *
 * <h2>판정은 고정 스키마로 받는다</h2>
 * 자유 텍스트를 파싱하면 형식이 흔들려 "통과인지 반려인지"가 불안정해진다. structured outputs 로
 * 스키마를 고정해 항상 같은 모양으로 받는다. 고정이 걸리려면 {@code strict: true} 와
 * <b>모든 object 의 {@code additionalProperties: false}</b> 가 함께 있어야 한다 — 하나만 있으면
 * 요청이 거부된다.
 */
@Slf4j
@Component
public class OpenAiVerificationReviewClient {
    private static final String RESPONSES_URI = "https://api.openai.com/v1/responses";
    private static final String SCHEMA_NAME = "verification_review";

    /**
     * 이미지 해상도 정책.
     *
     * <p><b>{@code auto} 는 모델이 원본 패치 수를 그대로 쓴다는 뜻이다</b> — 서버가 패치 예산이나
     * 픽셀 상한으로 줄여 주지 않는다. 그래서 비용을 정하는 것은 우리가 보내기 전에 줄이는
     * {@code max-image-dimension} 하나뿐이고, 조절 손잡이를 이 값과 둘로 나누지 않으려고
     * 여기서 고정한다. 손잡이가 둘이면 서로 상쇄돼 어느 쪽이 듣는지 알 수 없다.
     */
    private static final String IMAGE_DETAIL = "auto";

    /** 이 형식들만 받는다. 그 밖의 형식은 요청 자체가 거부되므로 부르기 전에 거른다. */
    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /** 반려 사유가 길면 화면이 깨진다. 한 문장 정도로 자른다. */
    private static final int MAX_REASON_LENGTH = 200;

    private final RestClient restClient;
    private final AiReviewProperties properties;

    /**
     * 응답 본문의 JSON 문자열을 푸는 데만 쓴다.
     *
     * <p>애플리케이션 공용 {@code ObjectMapper} 를 주입받지 않는 이유는, 전역 설정이 바뀌면
     * 이 파싱까지 함께 흔들리기 때문이다. 여기서 읽는 것은 외부 API 계약이라 우리 직렬화 정책과
     * 무관해야 한다.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiVerificationReviewClient(AiReviewProperties properties) {
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
        if (!SUPPORTED_MIME_TYPES.contains(image.mimeType())) {
            // 다시 불러도 같은 결과라 보류 대상이 아니다. 보류로 두면 형식이 안 맞는 사진 하나가
            // 상한까지 재심사를 반복하다 결국 통과한다.
            log.warn("심사기가 받지 않는 이미지 형식이라 건너뜁니다. challenge={}, 형식={}",
                    challengeName, image.mimeType());
            return VerificationReview.notApplicable("지원하지 않는 이미지 형식: " + image.mimeType());
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri(RESPONSES_URI)
                    .header("Authorization", "Bearer " + properties.getApiKey())
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

    /**
     * 출력 토큰 상한을 두지 않는다.
     *
     * <p>스키마가 필드 세 개짜리 object 로 고정돼 있어 길어질 여지가 없고, 상한 필드 이름을
     * 잘못 쓰면 요청이 400 으로 거부되는데 이 기능은 fail-open 이라 <b>그 실패가 "심사가 꺼진
     * 것"과 구분되지 않는다.</b> 얻는 것이 없는 자리에서 그 위험을 지지 않는다.
     */
    private Map<String, Object> requestBody(String name, String description, MediaImage image) {
        String dataUrl = "data:" + image.mimeType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.bytes());
        return Map.of(
                "model", properties.getModel(),
                "input", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "input_image",
                                        "image_url", dataUrl,
                                        "detail", IMAGE_DETAIL),
                                Map.of("type", "input_text",
                                        "text", prompt(name, description))))),
                "text", Map.of("format", reviewFormat()));
    }

    private Map<String, Object> reviewFormat() {
        return Map.of(
                "type", "json_schema",
                "name", SCHEMA_NAME,
                // strict 와 additionalProperties 는 짝이다. 하나만 있으면 스키마가 거부된다.
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
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
                        // strict 모드는 모든 property 가 required 여야 하므로 셋 다 넣는다.
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
                """.formatted(intent);
    }

    /**
     * 응답에서 판정을 꺼낸다.
     *
     * <p>순서가 중요하다. <b>거부 파트를 텍스트 파트보다 먼저 본다</b> — 거부를 못 알아보면
     * 판정 JSON 이 없다는 이유로 "해석 실패"가 되고, 그건 fail-open 이라 통과가 된다.
     */
    @SuppressWarnings("unchecked")
    private VerificationReview parse(Map<String, Object> response, String challengeName) {
        if (response == null) {
            return unparsable("응답이 비어 있습니다", challengeName);
        }
        if (!(response.get("output") instanceof List<?> output)) {
            return unparsable("output 이 없습니다", challengeName);
        }

        String reviewJson = null;
        for (Object item : output) {
            if (!(item instanceof Map<?, ?> message) || !"message".equals(message.get("type"))) {
                continue;
            }
            if (!(message.get("content") instanceof List<?> parts)) {
                continue;
            }
            for (Object part : parts) {
                if (!(part instanceof Map<?, ?> block)) {
                    continue;
                }
                Object type = block.get("type");
                if ("refusal".equals(type)) {
                    // 모델이 안전상 응답을 거부한 경우다. 이것을 "심사 못 함"으로 흘리면 통과가
                    // 되는데, 그러면 가장 걸러야 할 사진이 가장 확실하게 통과한다.
                    // 거부는 장애가 아니라 판단이다.
                    log.warn("모델이 안전상 심사를 거부해 반려합니다. challenge={}, 사유={}",
                            challengeName, block.get("refusal"));
                    return VerificationReview.reject(ReviewRejection.UNSAFE,
                            "공개하기 어려운 사진으로 판단되었습니다.");
                }
                if ("output_text".equals(type) && block.get("text") instanceof String text) {
                    reviewJson = text;
                }
            }
        }

        if (reviewJson == null) {
            // 스키마를 고정했는데도 안 왔다면 모델이나 API 계약이 바뀐 것이다. 막지 않고 드러낸다.
            return unparsable("판정 결과가 없습니다", challengeName);
        }

        Map<String, Object> values;
        try {
            values = objectMapper.readValue(reviewJson, Map.class);
        } catch (Exception e) {
            return unparsable("판정 JSON 을 읽지 못했습니다", challengeName);
        }

        if (!(values.get("approved") instanceof Boolean decision)) {
            return unparsable("approved 가 없습니다", challengeName);
        }
        if (decision) {
            return VerificationReview.pass();
        }

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
