package com.lirouti.domain.notification.scheduler;

import com.google.firebase.messaging.FirebaseMessaging;
import com.lirouti.domain.notification.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 프로세스 종료 등으로 배송 lease가 만료된 알림을 다시 처리한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(FirebaseMessaging.class)
public class NotificationDeliveryRecoveryScheduler {
    private final NotificationDeliveryService deliveryService;

    /** 1분마다 만료된 SENDING 알림을 제한된 배치 크기로 복구한다. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void recoverExpiredDeliveries() {
        deliveryService.recoverExpiredDeliveries();
    }
}
