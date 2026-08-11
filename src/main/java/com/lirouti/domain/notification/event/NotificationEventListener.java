package com.lirouti.domain.notification.event;

import com.lirouti.domain.notification.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 원래 업무 커밋 뒤 알림을 독립 저장하고, FCM이 켜져 있으면 Android로 전송한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final NotificationDispatchService dispatchService;

    /** 알림 실패가 좋아요·신고·가입 같은 원래 업무 결과를 되돌리지 않게 AFTER_COMMIT에서 처리한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NotificationRequestedEvent event) {
        try {
            dispatchService.dispatch(event.memberId(), event.category(), event.type(),
                    event.title(), event.body(), event.groupId(), event.referenceId(),
                    event.referenceType(), event.deduplicationKey());
        } catch (RuntimeException exception) {
            log.error("업무 커밋 후 알림 처리에 실패했습니다. key={}", event.deduplicationKey(), exception);
        }
    }
}
