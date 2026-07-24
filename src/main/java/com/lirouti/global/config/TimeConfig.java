package com.lirouti.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 그룹 루틴 할당과 스케줄러가 동일한 한국 시간 기준을 사용하도록 Clock을 제공한다.
     *
     * @return 한국 표준시 시스템 Clock
     */
    @Bean
    public Clock kstClock() {
        return Clock.system(KST);
    }
}
