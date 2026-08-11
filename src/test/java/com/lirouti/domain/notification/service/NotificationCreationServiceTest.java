package com.lirouti.domain.notification.service;

import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCreationService 알림 생성 테스트")
class NotificationCreationServiceTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private NotificationRepository repository;
    @Mock
    private NotificationSettingPolicy settingPolicy;

    @InjectMocks
    private NotificationCreationService creationService;

    @Test
    @DisplayName("사용자가 끈 알림은 행을 생성하지 않아 Push 식별자도 반환하지 않는다")
    void create_DisabledSetting_SkipsPersistence() {
        when(settingPolicy.allows(MEMBER_ID, NotificationType.GROUP_CHAT_MESSAGE)).thenReturn(false);

        Long result = createChatNotification();

        assertThat(result).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("사용자가 켠 알림은 저장하고 신규 알림 ID를 반환한다")
    void create_EnabledSetting_ReturnsInsertedId() {
        when(settingPolicy.allows(MEMBER_ID, NotificationType.GROUP_CHAT_MESSAGE)).thenReturn(true);
        when(repository.insertOrTouch(
                MEMBER_ID, "CHAT", "GROUP_CHAT_MESSAGE", "새 메시지", "안녕하세요",
                10L, 20L, "CHAT_MESSAGE", "chat-message:20:1"
        )).thenReturn(1);
        when(repository.currentLastInsertId()).thenReturn(42L);

        Long result = createChatNotification();

        assertThat(result).isEqualTo(42L);
        verify(repository).currentLastInsertId();
    }

    @Test
    @DisplayName("중복 알림은 저장소 결과에 따라 ID 조회를 생략한다")
    void create_DuplicateEvent_SkipsIdLookup() {
        when(settingPolicy.allows(MEMBER_ID, NotificationType.GROUP_CHAT_MESSAGE)).thenReturn(true);
        when(repository.insertOrTouch(
                MEMBER_ID, "CHAT", "GROUP_CHAT_MESSAGE", "새 메시지", "안녕하세요",
                10L, 20L, "CHAT_MESSAGE", "chat-message:20:1"
        )).thenReturn(0);

        Long result = createChatNotification();

        assertThat(result).isNull();
        verify(repository, never()).currentLastInsertId();
    }

    private Long createChatNotification() {
        return creationService.create(
                MEMBER_ID,
                NotificationCategory.CHAT,
                NotificationType.GROUP_CHAT_MESSAGE,
                "새 메시지",
                "안녕하세요",
                10L,
                20L,
                "CHAT_MESSAGE",
                "chat-message:20:1"
        );
    }
}
