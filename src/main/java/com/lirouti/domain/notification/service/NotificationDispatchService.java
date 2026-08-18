package com.lirouti.domain.notification.service;

import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 알림 행을 중복 없이 생성하고 FCM이 활성화돼 있으면 즉시 배송한다. */
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {
    private final NotificationCreationService creationService;
    private final ObjectProvider<NotificationDeliveryService> deliveryProvider;

    /**
     * 알림 설정과 중복 방지 정책을 적용해 앱 내 알림을 만들고 선택적으로 Push를 보낸다.
     *
     * @param memberId 수신 회원 ID
     * @param category 알림센터 분류
     * @param type 알림 사건 유형
     * @param title 알림 제목
     * @param body 알림 본문
     * @param groupId 관련 그룹 ID
     * @param referenceId 화면 이동 대상 ID
     * @param referenceType 화면 이동 대상 종류
     * @param deduplicationKey 회원 안에서 유일한 사건 키
     */
    public void dispatch(
            Long memberId,
            NotificationCategory category,
            NotificationType type,
            String title,
            String body,
            Long groupId,
            Long referenceId,
            String referenceType,
            String deduplicationKey
    ) {
        Long notificationId = creationService.create(
                memberId,
                category,
                type,
                title,
                body,
                groupId,
                referenceId,
                referenceType,
                deduplicationKey
        );
        NotificationDeliveryService deliveryService = deliveryProvider.getIfAvailable();
        if (notificationId != null && deliveryService != null) {
            deliveryService.deliver(notificationId);
        }
    }
}
