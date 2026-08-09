package com.lirouti.domain.notification.event;

import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;

/** 업무 트랜잭션 성공 뒤 생성할 사용자 알림의 불변 스냅샷이다. */
public record NotificationRequestedEvent(
        Long memberId, NotificationCategory category, NotificationType type,
        String title, String body, Long groupId, Long referenceId,
        String referenceType, String deduplicationKey
) {}
