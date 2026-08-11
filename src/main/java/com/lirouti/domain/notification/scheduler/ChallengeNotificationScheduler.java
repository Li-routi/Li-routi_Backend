package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** KST 날짜 경계에서 활성 챌린지의 새 수행 주기 알림을 처리한다. */
@Component
@RequiredArgsConstructor
public class ChallengeNotificationScheduler {
    private final MemberChallengeRepository participationRepository;
    private final NotificationDispatchService dispatchService;
    private final Clock clock;

    /** 현재 DAILY 챌린지 정책에 따라 매일 자정 새 주기 알림을 생성한다. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void notifyChallengeCycleStart() {
        LocalDate today = LocalDate.now(clock);
        for (MemberChallenge participation
                : participationRepository.findAllActiveForCycleNotification()) {
            dispatchService.dispatch(
                    participation.getMember().getId(),
                    NotificationCategory.CHALLENGE,
                    NotificationType.CHALLENGE_CYCLE_STARTED,
                    "새 챌린지 주기가 시작됐어요",
                    participation.getChallenge().getName() + "에 오늘도 도전해 보세요!",
                    null,
                    participation.getChallenge().getId(),
                    "CHALLENGE",
                    "challenge-cycle:" + participation.getId() + ":" + today
            );
        }
    }
}
