package com.lirouti.domain.verification.service.command;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.achievement.service.command.MemberRoutineStreakCommandService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.group.service.command.GroupRoutineAssignmentCommandService;
import com.lirouti.domain.group.service.command.GroupMemberActivityCommandService;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationDisappointmentRepository;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;

import com.lirouti.domain.activity.service.command.MemberActivityDayCommandService;
import com.lirouti.domain.character.service.command.CharacterUnlockCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineVerificationCommandService {

    private static final String CONDITION_KEY_ROUTINE_COMPLETE_COUNT = "ROUTINE_COMPLETE_COUNT";
    private static final String SOURCE_TYPE_MEMBER_ROUTINE_VERIFICATION = "MEMBER_ROUTINE_VERIFICATION";
    private static final String SOURCE_TYPE_GROUP_ROUTINE_VERIFICATION = "GROUP_ROUTINE_VERIFICATION";

    /**
     * ACH-EG-003(일찍 일어난 새, 히든) conditionKey. 오전 7시 이전 완료마다 1씩 누적된다
     * (CUMULATIVE_COUNT target=5).
     */
    private static final String CONDITION_KEY_EARLY_MORNING_COMPLETE_COUNT = "EARLY_MORNING_COMPLETE_COUNT";
    private static final LocalTime EARLY_MORNING_CUTOFF = LocalTime.of(7, 0);

    /**
     * ACH-EG-011(딱 1분 남았어!, 히든) conditionKey. NONE 타입이라 이 이벤트가 한 번만
     * 반영돼도 즉시 달성 처리된다. 세부조건: 마감까지 1초 이상 60초 이하 남았을 때 완료해야
     * 인정 — 마감과 같거나 지난 뒤 완료하면 인정하지 않는다.
     */
    private static final String CONDITION_KEY_DEADLINE_LAST_MINUTE_COMPLETE = "DEADLINE_LAST_MINUTE_COMPLETE";
    private static final Duration DEADLINE_LAST_MINUTE_MIN = Duration.ofSeconds(1);
    private static final Duration DEADLINE_LAST_MINUTE_MAX = Duration.ofSeconds(60);

    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;
    private final GroupValidationService groupValidationService;
    private final GroupRoutineVerificationLikeRepository groupRoutineVerificationLikeRepository;
    private final GroupRoutineVerificationDisappointmentRepository disappointmentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;
    private final MemberRoutineStreakCommandService memberRoutineStreakCommandService;

    private final ApplicationEventPublisher eventPublisher;
    private final MemberActivityDayCommandService memberActivityDayCommandService;
    private final CharacterUnlockCommandService characterUnlockCommandService;

    @Transactional
    public GroupRoutineVerification verifyGroupRoutine(
            Long memberId, Long groupId, Long routineId, LocalDate assignedDate,
            String mediaKey, String content, LocalDateTime verifiedAt
    ) {
        groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateActiveGroupMember(groupId, memberId);
        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository
                .findForVerification(routineId, groupId, memberId, assignedDate)
                .orElseThrow(() -> {
                    log.warn("오늘 수행할 그룹 루틴 할당이 없습니다. memberId={}, groupId={}, routineId={}",
                            memberId, groupId, routineId);
                    return new VerificationException(VerificationErrorCode.ASSIGNMENT_NOT_FOUND);
                });

        if (groupRoutineVerificationRepository.findByAssignmentId(assignment.getId()).isPresent()) {
            throw new VerificationException(VerificationErrorCode.ALREADY_VERIFIED);
        }

        assignmentCommandService.validateAssignmentVerifiable(assignment, verifiedAt);
        return saveGroupRoutine(assignment, mediaKey, content, verifiedAt);
    }

    @Transactional
    public GroupRoutineVerification saveGroupRoutine(
            GroupRoutineAssignment assignment, String mediaKey, String content, LocalDateTime verifiedAt
    ) {
        GroupRoutineVerification verification = GroupRoutineVerification.builder()
                .assignment(assignment)
                .verifiedAt(verifiedAt)
                .imageUrl(mediaKey)
                .content(content)
                .build();
        assignment.attachVerification(verification);
        GroupRoutineVerification saved =
                save(() -> groupRoutineVerificationRepository.saveAndFlush(verification));

        publishEarlyMorningEventIfApplicable(
                assignment.getMember().getId(), saved.getVerifiedAt(),
                SOURCE_TYPE_GROUP_ROUTINE_VERIFICATION, saved.getId());
        publishDeadlineLastMinuteEventIfApplicable(
                assignment.getMember().getId(), saved.getVerifiedAt(),
                SOURCE_TYPE_GROUP_ROUTINE_VERIFICATION, saved.getId(),
                assignment.getAssignedDate().atTime(assignment.getScheduledEndTime()));

        memberActivityDayCommandService.record(
                assignment.getMember().getId(), verifiedAt.toLocalDate());
        characterUnlockCommandService.evaluateAndUnlock(assignment.getMember().getId());

        return saved;
    }

    @Transactional
    public GroupRoutineVerification reverifyGroupRoutine(
            Long memberId, Long groupId, Long routineId, Long verificationId, LocalDate assignedDate,
            String mediaKey, String content, LocalDateTime verifiedAt
    ) {
        groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateActiveGroupMember(groupId, memberId);
        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository
                .findForVerification(routineId, groupId, memberId, assignedDate)
                .orElseThrow(() -> new VerificationException(VerificationErrorCode.ASSIGNMENT_NOT_FOUND));
        assignmentCommandService.validateAssignmentVerifiable(assignment, verifiedAt);
        GroupRoutineVerification verification = groupRoutineVerificationRepository.findByAssignmentId(assignment.getId())
                .filter(found -> found.getId().equals(verificationId))
                .orElseThrow(() -> new VerificationException(
                        VerificationErrorCode.GROUP_ROUTINE_VERIFICATION_NOT_FOUND));

        int deletedLikes = groupRoutineVerificationLikeRepository.deleteAllByVerificationId(verificationId);
        int deletedDisappointments = disappointmentRepository.deleteAllByVerificationId(verificationId);
        if (deletedLikes > 0 || deletedDisappointments > 0) {
            GroupMember authorMembership = groupMemberActivityCommandService.lockMembership(groupId, memberId);
            if (deletedLikes > 0) {
                groupMemberRepository.decrementTotalLikeCountForCurrentActiveMembershipIfPositive(
                        authorMembership.getId(), assignment.getId(), deletedLikes);
            }
            if (deletedDisappointments > 0) {
                groupMemberRepository.decrementTotalDisappointmentCountForCurrentActiveMembershipIfPositive(
                        authorMembership.getId(), assignment.getId(), deletedDisappointments);
            }
        }
        verification.reverify(mediaKey, content, verifiedAt);

        // 재인증은 히든 업적 조건도 다시 반영하지 않는다. 새로운 완료가 아니라 사진 교체이므로,
        // 재인증 때마다 "오전 7시 이전"·"마감 임박" 진행도가 중복으로 오르면 원래 인증 시점과
        // 무관하게 조건을 충족시킬 수 있다.
        return verification;
    }

    @Transactional
    public MemberRoutineVerification saveMemberRoutine(
            MemberRoutine routine, String mediaKey, String content,
            LocalDate verifiedDate, LocalDateTime verifiedAt
    ) {
        MemberRoutineVerification verification = MemberRoutineVerification.builder()
                .memberRoutine(routine)
                .verifiedDate(verifiedDate)
                .verifiedAt(verifiedAt)
                .imageUrl(mediaKey)
                .content(content)
                .build();
        MemberRoutineVerification saved = save(() -> memberRoutineVerificationRepository.saveAndFlush(verification));
        publishRoutineCompleteEvent(routine, saved);

        Long memberId = routine.getMember().getId();
        publishEarlyMorningEventIfApplicable(
                memberId, saved.getVerifiedAt(),
                SOURCE_TYPE_MEMBER_ROUTINE_VERIFICATION, saved.getId());
        publishDeadlineLastMinuteEventIfApplicable(
                memberId, saved.getVerifiedAt(),
                SOURCE_TYPE_MEMBER_ROUTINE_VERIFICATION, saved.getId(),
                verifiedDate.atTime(routine.getEndTime()));

        memberRoutineStreakCommandService.recordCompletion(
                memberId, verifiedDate, verifiedAt,
                SOURCE_TYPE_MEMBER_ROUTINE_VERIFICATION, saved.getId());

        memberActivityDayCommandService.recordWithCompletion(memberId, verifiedDate);
        characterUnlockCommandService.evaluateAndUnlock(memberId);
        return saved;
    }

    private void publishRoutineCompleteEvent(MemberRoutine routine, MemberRoutineVerification saved) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publishEvent(new AchievementProgressEvent(
                routine.getMember().getId(),
                CONDITION_KEY_ROUTINE_COMPLETE_COUNT,
                1,
                SOURCE_TYPE_MEMBER_ROUTINE_VERIFICATION,
                saved.getId(),
                routine.getCategory().getId(),
                saved.getVerifiedAt()
        ));
    }

    /**
     * "일찍 일어난 새"(ACH-EG-003) 진행도. 개인·그룹 루틴 인증 모두에 적용한다 —
     * 스펙상 "오전 7시 이전에 루틴을 완료"이지 개인 루틴으로 한정하지 않는다.
     */
    private void publishEarlyMorningEventIfApplicable(
            Long memberId, LocalDateTime verifiedAt, String sourceType, Long sourceId
    ) {
        if (eventPublisher == null || !verifiedAt.toLocalTime().isBefore(EARLY_MORNING_CUTOFF)) {
            return;
        }
        eventPublisher.publishEvent(new AchievementProgressEvent(
                memberId, CONDITION_KEY_EARLY_MORNING_COMPLETE_COUNT, 1, sourceType, sourceId
        ));
    }

    /**
     * "딱 1분 남았어!"(ACH-EG-011) 진행도. 마감까지 1~60초 남은 시점에 완료해야 반영된다
     * (경계값 포함, 마감과 같거나 지난 시점은 제외).
     */
    private void publishDeadlineLastMinuteEventIfApplicable(
            Long memberId, LocalDateTime verifiedAt, String sourceType, Long sourceId, LocalDateTime deadline
    ) {
        if (eventPublisher == null) {
            return;
        }
        Duration remaining = Duration.between(verifiedAt, deadline);
        if (remaining.compareTo(DEADLINE_LAST_MINUTE_MIN) < 0
                || remaining.compareTo(DEADLINE_LAST_MINUTE_MAX) > 0) {
            return;
        }
        eventPublisher.publishEvent(new AchievementProgressEvent(
                memberId, CONDITION_KEY_DEADLINE_LAST_MINUTE_COMPLETE, 1, sourceType, sourceId
        ));
    }

    private <T> T save(java.util.function.Supplier<T> persist) {
        try {
            return persist.get();
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            throw new VerificationException(VerificationErrorCode.VERIFICATION_CONFLICT);
        }
    }
}
