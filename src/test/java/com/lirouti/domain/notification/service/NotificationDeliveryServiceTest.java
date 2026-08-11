package com.lirouti.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.lirouti.domain.notification.enums.PushStatus;
import com.lirouti.domain.notification.repository.FcmDeviceRepository;
import com.lirouti.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FCM 알림 배송 테스트")
class NotificationDeliveryServiceTest {
    @Mock
    private FirebaseMessaging firebaseMessaging;
    @Mock
    private FcmDeviceRepository deviceRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private NotificationDeliveryService deliveryService;

    @Test
    @DisplayName("다른 호출이 선점한 알림은 Firebase로 중복 전송하지 않는다")
    void deliver_AlreadyClaimed_SkipsFirebaseCall() {
        when(notificationRepository.claimPendingDelivery(
                42L,
                PushStatus.PENDING,
                PushStatus.SENDING
        )).thenReturn(0);

        deliveryService.deliver(42L);

        verify(notificationRepository).claimPendingDelivery(
                42L,
                PushStatus.PENDING,
                PushStatus.SENDING
        );
        verifyNoInteractions(firebaseMessaging, deviceRepository, clock);
    }
}
