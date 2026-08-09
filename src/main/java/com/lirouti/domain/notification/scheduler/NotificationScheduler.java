package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.notification.enums.*;
import com.lirouti.domain.notification.repository.NotificationRepository;
import com.lirouti.domain.notification.service.NotificationCreationService;
import com.lirouti.domain.notification.service.NotificationDeliveryService;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

/** KST 분 경계에서 미완료 루틴을 찾아 중복 없는 자극형 Android 알림을 만든다. */
@Component @RequiredArgsConstructor
public class NotificationScheduler {
    private final MemberRoutineRepository routineRepository;
    private final MemberRoutineVerificationRepository verificationRepository;
    private final GroupRoutineAssignmentRepository assignmentRepository;
    private final MemberChallengeRepository participationRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationCreationService creationService;
    private final ObjectProvider<NotificationDeliveryService> deliveryProvider;
    private final Clock clock;

    /** 선택 알림 시각과 마감 1시간 전의 개인 루틴을 매분 확인한다. */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void notifyPersonalRoutines() {
        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        notifyPersonal(now, false);
        notifyPersonal(now.plusHours(1), true);
        notifyMissedPersonal(now);
    }

    private void notifyPersonal(LocalDateTime boundary, boolean deadline) {
        List<MemberRoutine> routines = routineRepository.findDueForNotification(boundary.getDayOfWeek(),
                boundary.toLocalTime(), boundary.toLocalTime().plusMinutes(1), deadline);
        for (MemberRoutine routine : routines) {
            if (verificationRepository.findByMemberRoutineIdAndVerifiedDate(
                    routine.getId(), boundary.toLocalDate()).isPresent()) continue;
            NotificationType type = deadline ? NotificationType.PERSONAL_ROUTINE_DEADLINE
                    : NotificationType.PERSONAL_ROUTINE_REMINDER;
            String title = deadline ? "루틴 마감까지 1시간 남았어요!" : routine.getName()+", 지금 시작해 볼까요?";
            String body = deadline ? routine.getName()+"을 완료하고 오늘 기록을 채워보세요 🔥"
                    : "작은 행동 하나가 오늘의 흐름을 만들어요.";
            createAndDeliver(routine.getMember().getId(), NotificationCategory.PERSONAL_ROUTINE, type,
                    title, body, null, routine.getId(), "MEMBER_ROUTINE",
                    "personal:"+type+":"+routine.getId()+":"+boundary.toLocalDate());
        }
    }

    /** 마감까지 완료하지 못한 개인 루틴에 듀오링고형 자극 문구를 보낸다. */
    private void notifyMissedPersonal(LocalDateTime boundary) {
        List<MemberRoutine> routines = routineRepository.findDueForNotification(
                boundary.getDayOfWeek(), boundary.toLocalTime(),
                boundary.toLocalTime().plusMinutes(1), true);
        for (MemberRoutine routine : routines) {
            if (verificationRepository.findByMemberRoutineIdAndVerifiedDate(
                    routine.getId(), boundary.toLocalDate()).isPresent()) {
                continue;
            }
            createAndDeliver(routine.getMember().getId(), NotificationCategory.PERSONAL_ROUTINE,
                    NotificationType.PERSONAL_ROUTINE_MISSED,
                    "오늘의 약속, 아직 기다리고 있어요",
                    routine.getName() + "을 놓쳤어요. 내일은 작은 한 번부터 다시 시작해요!",
                    null, routine.getId(), "MEMBER_ROUTINE",
                    "personal:missed:" + routine.getId() + ":" + boundary.toLocalDate());
        }
    }

    /** 그룹 할당 시작과 마감 1시간 전을 매분 확인한다. */
    @Scheduled(cron = "15 * * * * *", zone = "Asia/Seoul")
    public void notifyGroupRoutineBoundaries() {
        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        notifyGroup(now, true, NotificationType.GROUP_ROUTINE_STARTED);
        notifyGroup(now.plusHours(1), false, NotificationType.GROUP_ROUTINE_DEADLINE);
        notifyGroupEnded(now);
    }

    private void notifyGroup(LocalDateTime boundary, boolean start, NotificationType type) {
        List<GroupRoutineAssignment> assignments = assignmentRepository.findDueForNotification(
                boundary.toLocalDate(), boundary.toLocalTime(), boundary.toLocalTime().plusMinutes(1), start,
                List.of(GroupRoutineAssignmentStatus.COMPLETED, GroupRoutineAssignmentStatus.MISSED));
        for (GroupRoutineAssignment assignment : assignments) {
            String routineName = assignment.getGroupRoutine().getTitle();
            createAndDeliver(assignment.getMember().getId(), NotificationCategory.GROUP_ROUTINE, type,
                    start ? "그룹 루틴이 시작됐어요" : "그룹 루틴 마감까지 1시간!",
                    start ? routineName+"을 함께 시작해 보세요." : routineName+" 인증을 잊지 마세요 🔥",
                    assignment.getGroupRoutine().getGroup().getId(), assignment.getId(), "GROUP_ROUTINE_ASSIGNMENT",
                    "group-boundary:"+type+":"+assignment.getId());
        }
    }

    /** 수행 결과와 무관하게 그룹 루틴 종료 시각을 참여자별로 알린다. */
    private void notifyGroupEnded(LocalDateTime boundary) {
        for (GroupRoutineAssignment assignment : assignmentRepository.findEndingForNotification(
                boundary.toLocalDate(), boundary.toLocalTime(),
                boundary.toLocalTime().plusMinutes(1))) {
            createAndDeliver(assignment.getMember().getId(), NotificationCategory.GROUP_ROUTINE,
                    NotificationType.GROUP_ROUTINE_ENDED,
                    "그룹 루틴이 종료됐어요",
                    assignment.getGroupRoutine().getTitle() + "의 오늘 일정이 끝났어요.",
                    assignment.getGroupRoutine().getGroup().getId(), assignment.getId(),
                    "GROUP_ROUTINE_ASSIGNMENT", "group-ended:" + assignment.getId());
        }
    }

    /** 모든 현재 챌린지가 DAILY인 현 데이터에서 새 날짜 시작을 새 수행 주기로 알린다. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void notifyChallengeCycleStart() {
        LocalDate today = LocalDate.now(clock);
        for (MemberChallenge participation : participationRepository.findAllActiveForCycleNotification()) {
            createAndDeliver(participation.getMember().getId(), NotificationCategory.CHALLENGE,
                    NotificationType.CHALLENGE_CYCLE_STARTED, "새 챌린지 주기가 시작됐어요",
                    participation.getChallenge().getName()+"에 오늘도 도전해 보세요!", null,
                    participation.getChallenge().getId(), "CHALLENGE",
                    "challenge-cycle:"+participation.getId()+":"+today);
        }
    }

    /**
     * 디자인 정책에 맞춰 매일 새벽 최근 7일보다 오래된 알림을 정리한다.
     * derived delete 메서드는 쓰기 작업이라 트랜잭션 없이 호출하면 TransactionRequiredException이 난다.
     */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredNotifications() {
        notificationRepository.deleteByCreatedAtBefore(LocalDateTime.now(clock).minusDays(7));
    }

    private void createAndDeliver(Long memberId, NotificationCategory category, NotificationType type,
                                  String title, String body, Long groupId, Long referenceId,
                                  String referenceType, String key) {
        Long id = creationService.create(memberId, category, type, title, body, groupId,
                referenceId, referenceType, key);
        NotificationDeliveryService delivery = deliveryProvider.getIfAvailable();
        if (id != null && delivery != null) delivery.deliver(id);
    }
}
