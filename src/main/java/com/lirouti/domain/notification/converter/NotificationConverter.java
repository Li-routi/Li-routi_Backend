package com.lirouti.domain.notification.converter;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.notification.dto.response.NotificationResDTO;

/** 알림 도메인 엔티티를 API 응답으로 변환한다. */
public final class NotificationConverter {
    private NotificationConverter() {}

    /** 회원 엔티티의 여섯 알림 설정을 응답 DTO로 변환한다. */
    public static NotificationResDTO.Settings toSettings(Member member) {
        return new NotificationResDTO.Settings(
                member.isRoutineDeadlineNotificationEnabled(),
                member.isNewVerificationNotificationEnabled(),
                member.isVerificationReactionNotificationEnabled(),
                member.isPokeNotificationEnabled(),
                member.isNewChatNotificationEnabled(),
                member.isLikeNotificationEnabled()
        );
    }
}
