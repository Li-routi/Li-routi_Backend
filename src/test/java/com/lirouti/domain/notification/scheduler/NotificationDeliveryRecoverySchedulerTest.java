package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.notification.service.NotificationDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("알림 배송 lease 복구 스케줄러 테스트")
class NotificationDeliveryRecoverySchedulerTest {
    @Test
    @DisplayName("만료된 SENDING 알림 복구를 배송 서비스에 위임한다")
    void recoverExpiredDeliveries_DelegatesToDeliveryService() {
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        NotificationDeliveryRecoveryScheduler scheduler =
                new NotificationDeliveryRecoveryScheduler(deliveryService);

        scheduler.recoverExpiredDeliveries();

        verify(deliveryService).recoverExpiredDeliveries();
    }
}
