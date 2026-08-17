package com.lirouti.domain.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** GROUP_MEMBER_POKED 알림만 제한된 병렬도로 처리해 요청 DB connection을 보호한다. */
@Slf4j
@Configuration
public class NotificationTaskExecutorConfig {
    private static final int WORKER_COUNT = 4;
    private static final int QUEUE_CAPACITY = 100;

    @Bean(name = "notificationTaskExecutor")
    public ThreadPoolTaskExecutor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(WORKER_COUNT);
        executor.setMaxPoolSize(WORKER_COUNT);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler((task, pool) -> log.warn(
                "그룹원 poke 알림 작업을 버립니다. activeCount={}, poolSize={}, queueSize={}, queueCapacity={}",
                pool.getActiveCount(), pool.getPoolSize(), pool.getQueue().size(),
                pool.getQueue().size() + pool.getQueue().remainingCapacity()
        ));
        return executor;
    }
}
