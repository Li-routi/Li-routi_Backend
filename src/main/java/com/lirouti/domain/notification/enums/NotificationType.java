package com.lirouti.domain.notification.enums;

/** 클라이언트 화면 이동과 문구 선택에 사용하는 알림 사건 유형이다. */
public enum NotificationType {
    PERSONAL_ROUTINE_REMINDER(NotificationSettingType.ROUTINE_DEADLINE),
    PERSONAL_ROUTINE_DEADLINE(NotificationSettingType.ROUTINE_DEADLINE),
    PERSONAL_ROUTINE_MISSED(NotificationSettingType.ROUTINE_DEADLINE),
    CHALLENGE_VERIFICATION_LIKED(NotificationSettingType.LIKE),
    CHALLENGE_CYCLE_STARTED(NotificationSettingType.ALWAYS),
    CHALLENGE_RESTRICTED(NotificationSettingType.ALWAYS),
    CHALLENGE_REVIEW_APPROVED(NotificationSettingType.ALWAYS),
    CHALLENGE_REVIEW_REJECTED(NotificationSettingType.ALWAYS),
    GROUP_MEMBER_JOINED(NotificationSettingType.ALWAYS),
    GROUP_VERIFICATION_LIKED(NotificationSettingType.VERIFICATION_REACTION),
    GROUP_VERIFICATION_DISAPPOINTED(NotificationSettingType.VERIFICATION_REACTION),
    GROUP_MEMBER_POKED(NotificationSettingType.POKE),
    GROUP_ROUTINE_UPDATED(NotificationSettingType.ALWAYS),
    GROUP_CHAT_MESSAGE(NotificationSettingType.NEW_CHAT),
    GROUP_ROUTINE_STARTED(NotificationSettingType.ROUTINE_DEADLINE),
    GROUP_ROUTINE_DEADLINE(NotificationSettingType.ROUTINE_DEADLINE),
    GROUP_ROUTINE_ENDED(NotificationSettingType.ROUTINE_DEADLINE),
    GROUP_MEMBER_VERIFIED(NotificationSettingType.NEW_VERIFICATION);

    private final NotificationSettingType settingType;

    NotificationType(NotificationSettingType settingType) {
        this.settingType = settingType;
    }

    /** 이 사건을 수신할지 판단할 사용자 설정 종류를 반환한다. */
    public NotificationSettingType getSettingType() {
        return settingType;
    }
}
