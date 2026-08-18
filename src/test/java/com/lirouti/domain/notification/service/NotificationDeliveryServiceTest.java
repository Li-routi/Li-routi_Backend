package com.lirouti.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.enums.PushStatus;
import com.lirouti.domain.notification.repository.FcmDeviceRepository;
import com.lirouti.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        LocalDateTime claimedAt = LocalDateTime.of(2026, 8, 11, 12, 0);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-11T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(notificationRepository.claimPendingDelivery(
                42L,
                PushStatus.PENDING,
                PushStatus.SENDING,
                claimedAt,
                claimedAt.minusMinutes(5)
        )).thenReturn(0);

        deliveryService.deliver(42L);

        verify(notificationRepository).claimPendingDelivery(
                42L,
                PushStatus.PENDING,
                PushStatus.SENDING,
                claimedAt,
                claimedAt.minusMinutes(5)
        );
        verifyNoInteractions(firebaseMessaging, deviceRepository);
    }

    @Test
    @DisplayName("프로세스 종료로 lease가 만료된 SENDING 알림을 다시 배송 경로에 넣는다")
    void recoverExpiredDeliveries_ExpiredSending_RetriesDelivery() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 10);
        LocalDateTime expiredBefore = now.minusMinutes(5);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-11T12:10:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(notificationRepository.findExpiredDeliveryIds(
                PushStatus.SENDING,
                expiredBefore,
                PageRequest.of(0, 100)
        )).thenReturn(List.of(42L));
        when(notificationRepository.claimPendingDelivery(
                42L,
                PushStatus.PENDING,
                PushStatus.SENDING,
                now,
                expiredBefore
        )).thenReturn(0);

        deliveryService.recoverExpiredDeliveries();

        verify(notificationRepository).findExpiredDeliveryIds(
                PushStatus.SENDING,
                expiredBefore,
                PageRequest.of(0, 100)
        );
        verify(notificationRepository).claimPendingDelivery(
                42L,
                PushStatus.PENDING,
                PushStatus.SENDING,
                now,
                expiredBefore
        );
    }

    @Test
    @DisplayName("상세 화면 이동에 필요한 참조 ID를 FCM data에 포함한다")
    void data_WithReferenceId_IncludesReferenceId() {
        Notification notification = Notification.builder()
                .category(NotificationCategory.CHALLENGE)
                .type(NotificationType.CHALLENGE_VERIFICATION_LIKED)
                .title("좋아요 알림")
                .body("회원님의 인증에 좋아요가 달렸어요.")
                .referenceId(77L)
                .referenceType("CHALLENGE_VERIFICATION")
                .deduplicationKey("fcm-reference-id-test")
                .build();
        ReflectionTestUtils.setField(notification, "id", 42L);

        assertThat(deliveryService.data(notification))
                .containsEntry("notificationId", "42")
                .containsEntry("referenceId", "77")
                .containsEntry("type", NotificationType.CHALLENGE_VERIFICATION_LIKED.name());
    }
}
