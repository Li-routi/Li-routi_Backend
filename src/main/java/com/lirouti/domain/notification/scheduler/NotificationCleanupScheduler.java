package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 최근 7일 알림센터 정책 밖의 오래된 알림 행을 정리한다. */
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** 매일 KST 03:30에 최근 7일보다 오래된 알림을 삭제한다. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredNotifications() {
        notificationRepository.deleteByCreatedAtBefore(LocalDateTime.now(clock).minusDays(7));
    }
}
