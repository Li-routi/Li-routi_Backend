package com.lirouti.domain.notification.event;

import com.lirouti.domain.notification.service.NotificationCreationService;
import com.lirouti.domain.notification.service.NotificationDeliveryService;
import com.lirouti.domain.notification.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 원래 업무 커밋 뒤 알림을 독립 저장하고, FCM이 켜져 있으면 Android로 전송한다. */
@Slf4j
@Component
public class NotificationEventListener {
    private final NotificationCreationService creationService;
    private final ObjectProvider<NotificationDeliveryService> deliveryProvider;
    private final TaskExecutor notificationTaskExecutor;

    public NotificationEventListener(
            NotificationCreationService creationService,
            ObjectProvider<NotificationDeliveryService> deliveryProvider,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor
    ) {
        this.creationService = creationService;
        this.deliveryProvider = deliveryProvider;
        this.notificationTaskExecutor = notificationTaskExecutor;
    }

    /** 알림 실패가 좋아요·신고·가입 같은 원래 업무 결과를 되돌리지 않게 AFTER_COMMIT에서 처리한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NotificationRequestedEvent event) {
        if (event.type() == NotificationType.GROUP_MEMBER_POKED) {
            submitGroupMemberPoke(event);
            return;
        }
        process(event);
    }

    /** 누적 poke만 요청 트랜잭션과 분리해 bounded executor에서 best-effort로 처리한다. */
    private void submitGroupMemberPoke(NotificationRequestedEvent event) {
        try {
            notificationTaskExecutor.execute(() -> process(event));
        } catch (RuntimeException exception) {
            log.warn("그룹원 poke 알림 작업을 제출하지 못했습니다. key={}",
                    event.deduplicationKey(), exception);
        }
    }

    /** 기존 notification type이 사용하던 저장·배송 처리 경로다. */
    private void process(NotificationRequestedEvent event) {
        try {
            Long id = creationService.create(event.memberId(), event.category(), event.type(),
                    event.title(), event.body(), event.groupId(), event.referenceId(),
                    event.referenceType(), event.deduplicationKey());
            NotificationDeliveryService delivery = deliveryProvider.getIfAvailable();
            if (id != null && delivery != null) delivery.deliver(id);
        } catch (RuntimeException exception) {
            log.error("업무 커밋 후 알림 처리에 실패했습니다. key={}", event.deduplicationKey(), exception);
        }
    }
}
