package com.lirouti.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 스키마 이름이 서로 다른 DTO끼리 겹치지 않는지 본다.
 *
 * <h3>왜 이 테스트가 필요한가</h3>
 * springdoc은 스키마 이름을 <b>중첩 record의 단순 이름</b>으로 만든다. 서로 다른 DTO가 같은
 * 이름을 쓰면 나중에 등록된 하나가 나머지를 덮는데, <b>앱은 정상 동작하고 테스트도 통과한다.</b>
 * 문서만 조용히 틀린다.
 *
 * <p>실제로 네 쌍이 겹쳐 있었다. 그중 셋은 같은 DTO 쌍의 요청이 응답을 덮어,
 * presigned URL 발급 API의 문서화된 응답이 <b>요청 모양</b>으로 나가고 있었다 —
 * 문서를 보고 {@code uploadUrl}을 찾으면 없었다.
 *
 * <h3>왜 "중복 이름 전수 검사"가 아닌가</h3>
 * 완성된 문서의 {@code components.schemas}는 맵이라 <b>중복이 이미 합쳐진 뒤</b>다.
 * 문서만 봐서는 겹쳤다는 사실 자체를 알 수 없다. 그래서 "이 엔드포인트의 요청·응답이
 * 각각 제 필드를 갖고 있는가"를 확인하는 쪽으로 잡았다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("OpenAPI 스키마 이름 충돌 방지")
class OpenApiSchemaCollisionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("presigned URL 발급 — 엔드포인트가 요청·응답 스키마를 각각 제대로 가리킨다")
    void presignedUrl_RequestAndResponse_AreSeparateSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 요청: 엔드포인트 → 스키마
                .andExpect(jsonPath("$['paths']['/api/media/presigned-url']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/PresignedUrlRequest"))
                // 응답: 엔드포인트 → 래퍼 → result 스키마
                .andExpect(jsonPath("$['paths']['/api/media/presigned-url']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/ApiResponsePresignedUrlResult"))
                .andExpect(jsonPath("$['components']['schemas']['ApiResponsePresignedUrlResult']"
                        + "['properties']['result']['$ref']")
                        .value("#/components/schemas/PresignedUrlResult"))
                // 각 스키마가 제 필드를 갖는지 — 이름만 갈라두고 내용이 뒤바뀌면 소용없다
                .andExpect(jsonPath("$.components.schemas.PresignedUrlRequest.properties.purpose").exists())
                .andExpect(jsonPath("$.components.schemas.PresignedUrlRequest.properties.uploadUrl").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PresignedUrlResult.properties.uploadUrl").exists())
                .andExpect(jsonPath("$.components.schemas.PresignedUrlResult.properties.mediaKey").exists())
                .andExpect(jsonPath("$.components.schemas.PresignedUrlResult.properties.purpose").doesNotExist());
    }

    @Test
    @DisplayName("그룹 루틴 일정 — 생성 요청·결과가 각각 제 일정 스키마를 가리킨다")
    void groupRoutineSchedule_RequestAndResponse_AreSeparateSchemas() throws Exception {
        // 일정은 엔드포인트가 직접 가리키지 않고 생성 요청·결과 안에 배열로 들어간다.
        // 그래서 엔드포인트 → 생성 스키마 → 일정 스키마까지 따라간다.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/groups/{groupId}/routines']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/GroupRoutineCreateRequest"))
                .andExpect(jsonPath("$['components']['schemas']['GroupRoutineCreateRequest']"
                        + "['properties']['schedules']['items']['$ref']")
                        .value("#/components/schemas/GroupRoutineScheduleRequest"))
                .andExpect(jsonPath("$['paths']['/api/groups/{groupId}/routines']['post']"
                        + "['responses']['201']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/ApiResponseGroupRoutineCreateResult"))
                .andExpect(jsonPath("$['components']['schemas']['ApiResponseGroupRoutineCreateResult']"
                        + "['properties']['result']['$ref']")
                        .value("#/components/schemas/GroupRoutineCreateResult"))
                .andExpect(jsonPath("$['components']['schemas']['GroupRoutineCreateResult']"
                        + "['properties']['schedules']['items']['$ref']")
                        .value("#/components/schemas/GroupRoutineScheduleResult"))
                // 합쳐져 있던 옛 이름이 되살아나지 않는지
                .andExpect(jsonPath("$.components.schemas.RoutineSchedule").doesNotExist());
    }

    @Test
    @DisplayName("인증 신고 — 요청과 응답이 서로 다른 스키마다")
    void verificationReport_RequestAndResponse_AreSeparateSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']"
                        + "['/api/challenges/{challengeId}/verifications/{verificationId}/reports']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/ChallengeVerificationReportRequest"))
                .andExpect(jsonPath("$['paths']"
                        + "['/api/challenges/{challengeId}/verifications/{verificationId}/reports']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/ApiResponseChallengeVerificationReportResult"))
                .andExpect(jsonPath("$['components']['schemas']"
                        + "['ApiResponseChallengeVerificationReportResult']['properties']['result']['$ref']")
                        .value("#/components/schemas/ChallengeVerificationReportResult"))
                // 요청은 신고 사유, 응답은 만들어진 신고의 식별자. 겹쳐 있을 때는 응답이 사유 하나로 나갔다.
                .andExpect(jsonPath("$.components.schemas.ChallengeVerificationReportRequest"
                        + ".properties.reason").exists())
                .andExpect(jsonPath("$.components.schemas.ChallengeVerificationReportResult"
                        + ".properties.reportId").exists())
                .andExpect(jsonPath("$.components.schemas.ChallengeVerificationReportResult"
                        + ".properties.reason").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.Report").doesNotExist());
    }

    @Test
    @DisplayName("인증 요청 — 루틴과 챌린지가 서로 다른 스키마다")
    void verifyRequest_RoutineAndChallenge_AreSeparateSchemas() throws Exception {
        // 개인·그룹 루틴은 같은 DTO를 쓰므로 둘이 같은 스키마인 것이 정상이다.
        // 문제는 그것이 챌린지 것과도 같아지는 경우다 — 예시 mediaKey가 서로 다른 prefix라
        // 문서대로 따라 만들면 key 검증에서 거절된다.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/routines/{routineId}/verifications']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/RoutineVerifyRequest"))
                .andExpect(jsonPath("$['paths']['/api/groups/{groupId}/routines/{routineId}/verifications']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/RoutineVerifyRequest"))
                .andExpect(jsonPath("$['paths']['/api/challenges/{challengeId}/verifications']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/Verify"));
    }
}
