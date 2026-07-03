package com.lirouti.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                // Jackson 3에서는 날짜 직렬화 방식이 DateTimeFeature로 이동함
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 알려지지 않은 속성이 JSON에 있어도 DTO 변환 시 에러를 내지 않도록 설정
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }
}
