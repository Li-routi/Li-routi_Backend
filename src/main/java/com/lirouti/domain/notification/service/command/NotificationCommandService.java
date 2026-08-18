package com.lirouti.domain.notification.service.command;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Android 토큰 수명과 회원 소유 알림의 읽음 상태를 변경한다. */
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    /**
     * ACH-EG-007(까루) conditionKey. 시스템 알림을 눌러 앱에 접속했을 때만 발행된다 -
     * 앱 아이콘으로 직접 실행한 경우는 이 API 자체가 호출되지 않으므로 자연히 제외된다.
     * 하루 최대 1회로 제한하는 로직은 이벤트 발행 쪽이 아니라, sourceId를
     * "회원-오늘 날짜" 조합으로 만들어 achievement_progress_event_log의 유니크 제약이
     * 대신 막게 한다.
     */
    private static final String CONDITION_KEY_NOTIFICATION_CLICK_COUNT = "NOTIFICATION_CLICK_COUNT";
    private static final String SOURCE_TYPE_NOTIFICATION_CLICK = "NOTIFICATION_CLICK";

    private final FcmDeviceRepository fcmDeviceRepository;
    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

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

    /**
     * 회원이 시스템 알림을 눌러서 앱에 들어왔음을 기록한다. 읽음 처리와는 별개 동작이라
     * markRead를 호출하지 않는다 - 클릭했지만 아직 안 읽은 상태로 둘지는 클라이언트가
     * 별도로 markRead를 호출할지 결정할 문제다.
     *
     * <p>ACH-EG-007(알림 보고 왔어요) 진행도로 반영된다. sourceId를 "회원 id + 오늘 날짜"로
     * 조합해, achievement_progress_event_log의 유니크 제약이 하루 한 번만 실제 반영되게
     * 막는다 - 스펙의 "하루 최대 1회" 제한을 서비스 코드가 직접 세지 않고 DB 제약에 위임한다.
     */
    @Transactional
    public void markClicked(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        LocalDate today = LocalDate.now(clock);
        eventPublisher.publishEvent(new AchievementProgressEvent(
                memberId,
                CONDITION_KEY_NOTIFICATION_CLICK_COUNT,
                1,
                SOURCE_TYPE_NOTIFICATION_CLICK + ":" + today,  // 날짜를 sourceType에 포함
                memberId  // sourceId는 그냥 회원 id
        ));
    }

    /**
     * 회원 id와 날짜를 하나의 Long으로 합성한다. member_id가 10^9(약 10억) 미만이라는
     * 전제로, 회원 id를 상위 자리로 두고 날짜(YYYYMMDD, 8자리)를 하위 자리에 붙인다.
     * 이러면 같은 회원이라도 날짜가 다르면 sourceId가 달라져 매일 새로 카운트되고,
     * 같은 날 여러 번 클릭해도 같은 sourceId라 유니크 제약에 막혀 하루 1회로 제한된다.
     */
    private Long buildDailySourceId(Long memberId, LocalDate date) {
        long datePart = date.getYear() * 10_000L + date.getMonthValue() * 100L + date.getDayOfMonth();
        return memberId * 100_000_000L + datePart;
    }

    /** 현재 회원의 읽지 않은 알림을 한 번에 읽음 처리한다. */
    @Transactional
    public NotificationResDTO.ReadAll markAllRead(Long memberId) {
        return new NotificationResDTO.ReadAll(
                notificationRepository.markAllRead(memberId, LocalDateTime.now(clock)));
    }
}
