package com.lirouti.domain.notification.service;

import com.google.firebase.messaging.*;
import com.lirouti.domain.notification.entity.FcmDevice;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.PushStatus;
import com.lirouti.domain.notification.repository.FcmDeviceRepository;
import com.lirouti.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

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

    /**
     * FCM은 네트워크 호출이라 지연·장애 시간이 DB 커넥션 점유 시간이 되면 안 된다.
     * 그래서 이 메서드 전체를 하나의 @Transactional로 감싸지 않는다 — 조회·상태 갱신은
     * 각 repository 호출 단위의 짧은 트랜잭션으로 끝내고, sendEachForMulticast만 트랜잭션
     * 밖에서 실행한다. (같은 클래스 안에서 @Transactional 메서드를 셀프 호출하면 프록시를
     * 안 거쳐 트랜잭션이 안 걸리므로, 짧은 트랜잭션은 전부 repository 호출로만 구성한다.)
     */
    public void deliver(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) return;
        // 재전달 호출(예: 향후 재시도 로직)에서 이미 처리된 알림을 다시 보내지 않는다.
        if (notification.getPushStatus() != PushStatus.PENDING) return;

        List<FcmDevice> devices = deviceRepository.findAllByMemberIdAndActiveTrue(
                notification.getMember().getId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (devices.isEmpty()) {
            notification.markSkipped(now);
            notificationRepository.save(notification);
            return;
        }

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
            deviceRepository.saveAll(devices);
            // 배치에 실패 항목이 섞여 있어도 항상 SENT로 남기면, 실제로 못 받은 기기까지
            // 성공으로 기록된다. 최소 한 기기라도 성공했을 때만 SENT로 본다.
            if (response.getSuccessCount() > 0) {
                notification.markSent(now);
            } else {
                notification.markFailed(now);
            }
            notificationRepository.save(notification);
        } catch (FirebaseMessagingException exception) {
            notification.markFailed(now);
            notificationRepository.save(notification);
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
