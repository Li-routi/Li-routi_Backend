package com.lirouti.domain.notification.event;

import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.service.NotificationCreationService;
import com.lirouti.domain.notification.service.NotificationDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener 테스트")
class NotificationEventListenerTest {
    @Mock private NotificationCreationService creationService;
    @Mock private ObjectProvider<NotificationDeliveryService> deliveryProvider;
    @Mock private TaskExecutor notificationTaskExecutor;

    @Test
    @DisplayName("GROUP_MEMBER_POKED만 notification executor에 제출한다")
    void handle_GroupMemberPoked_SubmitsToNotificationExecutor() {
        NotificationEventListener listener = listener();
        NotificationRequestedEvent event = event(NotificationType.GROUP_MEMBER_POKED);

        listener.handle(event);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(notificationTaskExecutor).execute(taskCaptor.capture());
        verifyNoInteractions(creationService, deliveryProvider);

        taskCaptor.getValue().run();

        verify(creationService).create(
                event.memberId(), event.category(), event.type(), event.title(), event.body(),
                event.groupId(), event.referenceId(), event.referenceType(), event.deduplicationKey());
        verify(deliveryProvider).getIfAvailable();
    }

    @Test
    @DisplayName("executor 제출이 실패해도 예외를 전파하지 않는다")
    void handle_GroupMemberPoked_ExecutorRejects_DoesNotThrow() {
        NotificationEventListener listener = new NotificationEventListener(
                creationService, deliveryProvider, notificationTaskExecutor);
        doThrow(new TaskRejectedException("full"))
                .when(notificationTaskExecutor).execute(any(Runnable.class));

        listener.handle(event(NotificationType.GROUP_MEMBER_POKED));

        verify(notificationTaskExecutor).execute(any(Runnable.class));
        verifyNoInteractions(creationService, deliveryProvider);
    }

    @Test
    @DisplayName("다른 알림 type은 기존 동기 처리 경로를 유지한다")
    void handle_OtherNotificationType_ProcessesSynchronously() {
        NotificationEventListener listener = listener();
        NotificationRequestedEvent event = event(NotificationType.GROUP_MEMBER_JOINED);

        listener.handle(event);

        verifyNoInteractions(notificationTaskExecutor);
        verify(creationService).create(
                event.memberId(), event.category(), event.type(), event.title(), event.body(),
                event.groupId(), event.referenceId(), event.referenceType(), event.deduplicationKey());
        verify(deliveryProvider).getIfAvailable();
    }

    private NotificationEventListener listener() {
        when(creationService.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(deliveryProvider.getIfAvailable()).thenReturn(null);
        return new NotificationEventListener(creationService, deliveryProvider, notificationTaskExecutor);
    }

    private NotificationRequestedEvent event(NotificationType type) {
        return new NotificationRequestedEvent(
                2L, NotificationCategory.GROUP_ROUTINE, type,
                "알림 제목", "알림 본문", 1L, 3L, "GROUP_MEMBER", "notification-key"
        );
    }
}
