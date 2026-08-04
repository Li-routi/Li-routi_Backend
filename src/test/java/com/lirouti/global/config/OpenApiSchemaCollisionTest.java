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
    @DisplayName("presigned URL 발급 — 요청과 응답이 서로 다른 스키마다")
    void presignedUrl_RequestAndResponse_AreSeparateSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 요청: 무엇을 올릴 것인지
                .andExpect(jsonPath("$['paths']['/api/media/presigned-url']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/PresignedUrlRequest"))
                .andExpect(jsonPath("$.components.schemas.PresignedUrlRequest.properties.purpose").exists())
                .andExpect(jsonPath("$.components.schemas.PresignedUrlRequest.properties.uploadUrl").doesNotExist())
                // 응답: 어디로 올리면 되는지
                .andExpect(jsonPath("$.components.schemas.PresignedUrlResult.properties.uploadUrl").exists())
                .andExpect(jsonPath("$.components.schemas.PresignedUrlResult.properties.mediaKey").exists())
                .andExpect(jsonPath("$.components.schemas.PresignedUrlResult.properties.purpose").doesNotExist());
    }

    @Test
    @DisplayName("그룹 루틴 일정 — 요청과 응답이 서로 다른 스키마다")
    void groupRoutineSchedule_RequestAndResponse_AreSeparateSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineScheduleRequest").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineScheduleResult").exists())
                .andExpect(jsonPath("$.components.schemas.RoutineSchedule").doesNotExist());
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
