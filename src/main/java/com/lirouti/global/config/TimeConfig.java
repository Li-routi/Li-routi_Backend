package com.lirouti.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock kstClock() {
        return Clock.system(KST);
    }
}
