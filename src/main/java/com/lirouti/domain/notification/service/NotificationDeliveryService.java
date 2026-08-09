package com.lirouti.domain.notification.service;

import com.google.firebase.messaging.*;
import com.lirouti.domain.notification.entity.FcmDevice;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.repository.FcmDeviceRepository;
import com.lirouti.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 저장된 알림을 회원의 모든 활성 Android FCM 토큰으로 전송한다. */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FirebaseMessaging.class)
public class NotificationDeliveryService {
    private final FirebaseMessaging firebaseMessaging;
    private final FcmDeviceRepository deviceRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** FCM 실패가 원래 업무 트랜잭션으로 전파되지 않도록 호출자가 커밋한 뒤 사용한다. */
    @Transactional
    public void deliver(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) return;
        List<FcmDevice> devices = deviceRepository.findAllByMemberIdAndActiveTrue(
                notification.getMember().getId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (devices.isEmpty()) { notification.markSkipped(now); return; }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(devices.stream().map(FcmDevice::getToken).toList())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle()).setBody(notification.getBody()).build())
                .putAllData(data(notification))
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .build();
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            deactivateInvalidTokens(devices, response, now);
            notification.markSent(now);
        } catch (FirebaseMessagingException exception) {
            notification.markFailed(now);
            log.warn("FCM 전송에 실패했습니다. notificationId={}", notificationId, exception);
        }
    }

    private Map<String, String> data(Notification n) {
        return Map.of("notificationId", String.valueOf(n.getId()),
                "category", n.getCategory().name(), "type", n.getType().name());
    }

    private void deactivateInvalidTokens(List<FcmDevice> devices, BatchResponse response, LocalDateTime now) {
        for (int index = 0; index < response.getResponses().size(); index++) {
            SendResponse send = response.getResponses().get(index);
            if (send.isSuccessful() || send.getException() == null) continue;
            MessagingErrorCode code = send.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                devices.get(index).deactivate(now);
            }
        }
    }
}
