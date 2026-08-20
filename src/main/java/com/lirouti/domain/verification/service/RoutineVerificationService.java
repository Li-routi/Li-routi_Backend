package com.lirouti.domain.verification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;
import com.lirouti.domain.verification.service.command.RoutineVerificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 루틴 인증의 흐름을 엮는다. 사진 검증 → 소유권 확인 → 저장 → 저장 후 처리 순이다.
 *
 * <p>DB를 직접 다루지 않고 트랜잭션 경계도 갖지 않아 조회/변경 구분이 무의미하므로
 * CQRS를 적용하지 않는다(service_convention의 CQRS 예외 도메인). 트랜잭션은
 * {@link RoutineVerificationCommandService}가 갖는다 — 자기 호출로는 트랜잭션이 걸리지 않아
 * 빈을 나눴다(챌린지 인증과 같은 모양).
 *
 * <p><b>AI 심사는 붙이지 않는다.</b> 챌린지 인증에만 적용하기로 한 기획 확정 사항이다.
 * 루틴 인증은 사진 형식·바이트 검증까지만 거친다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineVerificationService {

    private final MediaService mediaService;
    private final MemberRoutineRepository memberRoutineRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;
    private final RoutineVerificationCommandService commandService;
    private final GroupMemberRepository groupMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 그룹 루틴 인증.
     *
     * <p>미디어 검증은 트랜잭션 밖에서 끝내고, Assignment 조회·잠금·인증 저장은
     * CommandService의 하나의 트랜잭션에서 수행한다. 탈퇴의 미완료 할당 삭제와 같은 행 잠금을
     * 공유해 먼저 확정된 요청의 결과를 따른다.
     */
    public VerificationResDTO.GroupRoutine verifyGroupRoutine(
            Long memberId,
            Long groupId,
            Long routineId,
            VerificationReqDTO.Verify request
    ) {
        mediaService.validateMediaKey(request.mediaKey(), MediaPurpose.GROUP_ROUTINE_VERIFICATION);
        mediaService.validateUploadedBytes(request.mediaKey(), MediaPurpose.GROUP_ROUTINE_VERIFICATION);

        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDateTime verifiedAt = now.toLocalDateTime();

        GroupRoutineVerification saved = commandService.verifyGroupRoutine(
                memberId,
                groupId,
                routineId,
                today,
                request.mediaKey(),
                request.content(),
                verifiedAt
        );

        publishGroupVerificationNotifications(saved, memberId, groupId,
                "group-verification:" + saved.getId());

        return new VerificationResDTO.GroupRoutine(
                saved.getId(), saved.getAssignment().getId(), saved.getImageUrl(),
                saved.getContent(), saved.getVerifiedAt());
    }

    /** 기존 인증의 interaction을 비우고 사진·내용을 교체한다. */
    public VerificationResDTO.GroupRoutine reverifyGroupRoutine(
            Long memberId,
            Long groupId,
            Long routineId,
            Long verificationId,
            VerificationReqDTO.Verify request
    ) {
        mediaService.validateMediaKey(request.mediaKey(), MediaPurpose.GROUP_ROUTINE_VERIFICATION);
        mediaService.validateUploadedBytes(request.mediaKey(), MediaPurpose.GROUP_ROUTINE_VERIFICATION);
        ZonedDateTime now = ZonedDateTime.now(clock);
        GroupRoutineVerification saved = commandService.reverifyGroupRoutine(
                memberId, groupId, routineId, verificationId, now.toLocalDate(), request.mediaKey(),
                request.content(), now.toLocalDateTime());
        publishGroupVerificationNotifications(saved, memberId, groupId,
                "group-reverification:" + saved.getId() + ":" + saved.getVerifiedAt());
        return new VerificationResDTO.GroupRoutine(
                saved.getId(), saved.getAssignment().getId(), saved.getImageUrl(),
                saved.getContent(), saved.getVerifiedAt());
    }

    /** 인증한 본인을 제외한 현재 그룹원에게 새 인증 소식을 전달한다. */
    private void publishGroupVerificationNotifications(
            GroupRoutineVerification verification,
            Long verifierId,
            Long groupId,
            String deduplicationPrefix
    ) {
        String verifierNickname = verification.getAssignment().getMember().getNickname();
        String routineTitle = verification.getAssignment().getGroupRoutine().getTitle();
        for (GroupMember groupMember : groupMemberRepository.findAllByGroupIdAndStatus(
                groupId, GroupMemberStatus.ACTIVE)) {
            Long recipientId = groupMember.getMember().getId();
            if (recipientId.equals(verifierId)) {
                continue;
            }
            eventPublisher.publishEvent(new NotificationRequestedEvent(
                    recipientId,
                    NotificationCategory.GROUP_ROUTINE,
                    NotificationType.GROUP_MEMBER_VERIFIED,
                    "새로운 그룹 루틴 인증이 올라왔어요!",
                    verifierNickname + "님이 " + routineTitle + " 인증을 완료했어요.",
                    groupId,
                    verification.getId(),
                    "GROUP_ROUTINE_VERIFICATION",
                    deduplicationPrefix + ":" + recipientId
            ));
        }
    }

    /**
     * 개인 루틴 인증.
     *
     * <p>개인 루틴에는 날짜별 행이 없으므로 <b>이 인증 행이 곧 완료 기록</b>이다.
     * 그래서 그룹과 달리 뒤에 이어 부를 상태 전이가 없다.
     */
    public VerificationResDTO.MemberRoutine verifyMemberRoutine(
            Long memberId,
            Long routineId,
            VerificationReqDTO.Verify request
    ) {
        mediaService.validateMediaKey(request.mediaKey(), MediaPurpose.MEMBER_ROUTINE_VERIFICATION);
        mediaService.validateUploadedBytes(request.mediaKey(), MediaPurpose.MEMBER_ROUTINE_VERIFICATION);

        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // 소유자와 활성 여부를 조회 조건에 넣는다. id로 찾아 뒤에서 비교하면
        // 응답만으로 그 id의 존재 여부가 드러난다.
        MemberRoutine routine = memberRoutineRepository
                .findByIdAndMemberIdAndActiveTrue(routineId, memberId)
                .orElseThrow(() -> {
                    log.warn("인증할 루틴을 찾지 못했습니다. memberId={}, routineId={}", memberId, routineId);
                    return new VerificationException(VerificationErrorCode.ROUTINE_NOT_FOUND);
                });

        // 등록할 때 정한 요일에만 인증할 수 있다. 아무 날이나 되면 "정해진 날짜"가 의미를 잃는다.
        boolean scheduledToday = routine.getSchedules().stream()
                .anyMatch(schedule -> schedule.getRepeatDay() == today.getDayOfWeek());
        if (!scheduledToday) {
            log.warn("수행 요일이 아닌 날의 인증을 차단했습니다. routineId={}, today={}",
                    routineId, today.getDayOfWeek());
            throw new VerificationException(VerificationErrorCode.NOT_SCHEDULED_TODAY);
        }

        validateMemberRoutineTimeRange(routine, now.toLocalTime());

        if (memberRoutineVerificationRepository
                .findByMemberRoutineIdAndVerifiedDate(routineId, today).isPresent()) {
            throw new VerificationException(VerificationErrorCode.ALREADY_VERIFIED);
        }

        MemberRoutineVerification saved = commandService.saveMemberRoutine(
                routine, request.mediaKey(), request.content(), today, now.toLocalDateTime());

        return new VerificationResDTO.MemberRoutine(
                saved.getId(), routineId, saved.getImageUrl(),
                saved.getContent(), saved.getVerifiedDate(), saved.getVerifiedAt());
    }

    /**
     * 현재 시각이 개인 루틴의 수행 가능 구간에 포함되는지 검증한다.
     *
     * <p>개인 루틴 시간은 API에서 {@code HH:mm} 단위로 받으므로 현재 시각도 분 단위로
     * 맞춘 뒤 시작·종료 시각을 모두 포함한다. 시작 시각이 없는 기존 루틴은 시작 제한만
     * 생략하며, 종료 시각이 속한 분까지 인증할 수 있다.
     *
     * @param routine 인증할 개인 루틴
     * @param currentTime KST 기준 현재 시각
     * @throws VerificationException 시작 전이거나 종료 시각 이후인 경우
     */
    private void validateMemberRoutineTimeRange(MemberRoutine routine, LocalTime currentTime) {
        LocalTime startTime = routine.getStartTime();
        LocalTime endTime = routine.getEndTime();
        LocalTime currentMinute = currentTime.truncatedTo(ChronoUnit.MINUTES);
        boolean beforeStart = startTime != null && currentMinute.isBefore(startTime);
        boolean afterEnd = currentMinute.isAfter(endTime);

        if (!beforeStart && !afterEnd) {
            return;
        }

        log.warn("개인 루틴 수행 시간 밖의 인증을 차단했습니다. "
                        + "routineId={}, currentTime={}, startTime={}, endTime={}",
                routine.getId(), currentMinute, startTime, endTime);
        throw new VerificationException(VerificationErrorCode.NOT_IN_ROUTINE_TIME_RANGE);
    }
}
