package com.lirouti.global.ratelimit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;

/**
 * 레이트 리밋이 실제 요청 경로에서 걸리는지 확인한다(#23).
 *
 * 단위 테스트는 인터셉터·Redis 계산을 각각 검증하지만, <b>인터셉터가 실제로 등록되어 있고
 * 예외가 429 응답으로 변환되는지</b>는 여기서만 확인된다.
 * 한도를 2로 낮춰 세 번째 요청이 막히는 것을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.enabled=true",
        "rate-limit.policies.media-presign.limit=2",
        "rate-limit.policies.media-presign.window=PT1H"
})
@DisplayName("레이트 리밋 통합 테스트")
class RateLimitIntegrationTest {
    private static final long MEMBER_ID = 99_001L;
    private static final String COUNTER_KEY = "rate-limit:media-presign:member:" + MEMBER_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("authRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetCounter() {
        // 창이 1시간이라 이전 실행분이 남아 있으면 첫 요청부터 막힌다. 이 테스트의 키만 지운다.
        redisTemplate.delete(COUNTER_KEY);
    }

    private org.springframework.test.web.servlet.ResultActions requestPresignedUrl() throws Exception {
        return mockMvc.perform(post("/api/media/presigned-url")
                .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"purpose":"CHALLENGE_VERIFICATION","contentType":"image/jpeg","contentLength":1024}
                        """));
    }

    @Test
    @DisplayName("한도를 넘긴 요청은 429와 Retry-After를 받는다")
    void exceedingLimit_Returns429WithRetryAfter() throws Exception {
        // 한도(2)까지는 통과한다. 발급 자체가 성공하는지는 여기서 관심이 아니므로 상태는 보지 않는다.
        requestPresignedUrl();
        requestPresignedUrl();

        requestPresignedUrl()
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON429_1"));
    }
}
