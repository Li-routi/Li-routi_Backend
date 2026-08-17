package com.lirouti.domain.notification.service;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.enums.NotificationSettingType;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 알림 사건 유형을 회원의 여섯 설정에 연결해 생성 허용 여부를 판단한다. */
@Component
@RequiredArgsConstructor
public class NotificationSettingPolicy {
    private final MemberRepository memberRepository;

    /** 항상 제공할 사건은 조회 없이 허용하고, 나머지는 회원 설정에 따라 결정한다. */
    public boolean allows(Long memberId, NotificationType notificationType) {
        NotificationSettingType settingType = notificationType.getSettingType();
        if (settingType == NotificationSettingType.ALWAYS) {
            return true;
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        return switch (settingType) {
            case ROUTINE_DEADLINE -> member.isRoutineDeadlineNotificationEnabled();
            case NEW_VERIFICATION -> member.isNewVerificationNotificationEnabled();
            case VERIFICATION_REACTION -> member.isVerificationReactionNotificationEnabled();
            case POKE -> member.isPokeNotificationEnabled();
            case NEW_CHAT -> member.isNewChatNotificationEnabled();
            case LIKE -> member.isLikeNotificationEnabled();
            case ALWAYS -> true;
        };
    }
}
