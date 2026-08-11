package com.lirouti.domain.notification.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.converter.NotificationConverter;
import com.lirouti.domain.notification.dto.request.NotificationReqDTO;
import com.lirouti.domain.notification.dto.response.NotificationResDTO;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.repository.FcmDeviceRepository;
import com.lirouti.domain.notification.repository.NotificationRepository;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Android 토큰 수명과 회원 소유 알림의 읽음 상태를 변경한다. */
@Service
@RequiredArgsConstructor
public class NotificationCommandService {
    private final FcmDeviceRepository fcmDeviceRepository;
    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    /** 토큰이 재발급되거나 다른 계정으로 로그인돼도 전역 유일 행 하나를 재사용한다. */
    @Transactional
    public NotificationResDTO.DeviceRegistration registerDevice(Long memberId, String token) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        fcmDeviceRepository.upsertActive(memberId, token, now);
        return new NotificationResDTO.DeviceRegistration(true);
    }

    /** 현재 회원 소유 토큰만 비활성화한다. 이미 비활성 또는 없는 요청은 멱등 성공한다. */
    @Transactional
    public NotificationResDTO.DeviceRegistration unregisterDevice(Long memberId, String token) {
        fcmDeviceRepository.deactivateOwnedToken(
                memberId,
                token,
                LocalDateTime.now(clock)
        );
        return new NotificationResDTO.DeviceRegistration(false);
    }

    /** 전달된 항목만 변경하도록 회원 행을 잠그고 변경 후 전체 설정을 반환한다. */
    @Transactional
    public NotificationResDTO.Settings updateSettings(
            Long memberId,
            NotificationReqDTO.UpdateSettings request
    ) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        member.updateNotificationSettings(
                request.routineDeadlineEnabled(),
                request.newVerificationEnabled(),
                request.verificationReactionEnabled(),
                request.pokeEnabled(),
                request.newChatEnabled(),
                request.likeEnabled()
        );
        return NotificationConverter.toSettings(member);
    }

    /** 다른 회원의 알림 ID는 존재 여부를 노출하지 않고 404로 처리한다. */
    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        notification.markRead(LocalDateTime.now(clock));
    }

    /** 현재 회원의 읽지 않은 알림을 한 번에 읽음 처리한다. */
    @Transactional
    public NotificationResDTO.ReadAll markAllRead(Long memberId) {
        return new NotificationResDTO.ReadAll(
                notificationRepository.markAllRead(memberId, LocalDateTime.now(clock)));
    }

}
