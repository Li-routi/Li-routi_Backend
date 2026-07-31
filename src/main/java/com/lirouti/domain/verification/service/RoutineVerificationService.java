package com.lirouti.domain.verification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.service.command.GroupRoutineAssignmentCommandService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;
import com.lirouti.domain.verification.service.command.RoutineVerificationCommandService;
import com.lirouti.global.util.TimeUtil;

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
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;
    private final RoutineVerificationCommandService commandService;

    /**
     * 그룹 루틴 인증.
     *
     * <p>완료 처리는 그룹 도메인의 기존 메서드에 맡긴다. 시간대 제약("수행 가능 시간이 아니다")과
     * 중복 완료 차단을 이미 그쪽이 하고 있어, 인증에서 다시 구현하면 규칙이 두 벌이 된다.
     *
     * <p><b>사진을 먼저 저장하고 완료 처리를 뒤에 부른다.</b> 순서를 뒤집으면 완료는 됐는데
     * 사진 저장이 실패하는 경우가 생겨, 사진 없는 완료가 남는다. 사진이 필수라는 결정과 어긋난다.
     */
    public VerificationResDTO.GroupRoutine verifyGroupRoutine(
            Long memberId,
            Long groupId,
            Long routineId,
            VerificationReqDTO.Verify request
    ) {
        mediaService.validateMediaKey(request.mediaKey(), MediaPurpose.GROUP_ROUTINE_VERIFICATION);
        mediaService.validateUploadedBytes(request.mediaKey(), MediaPurpose.GROUP_ROUTINE_VERIFICATION);

        ZonedDateTime now = ZonedDateTime.now(TimeUtil.KST);
        LocalDate today = now.toLocalDate();
        LocalDateTime verifiedAt = now.toLocalDateTime();

        GroupRoutineAssignment assignment = groupRoutineAssignmentRepository
                .findByGroupRoutineIdAndMemberIdAndAssignedDate(routineId, memberId, today)
                .filter(found -> found.getGroupRoutine().getGroup().getId().equals(groupId))
                .orElseThrow(() -> {
                    log.warn("오늘 수행할 그룹 루틴 할당이 없습니다. memberId={}, groupId={}, routineId={}",
                            memberId, groupId, routineId);
                    return new VerificationException(VerificationErrorCode.ASSIGNMENT_NOT_FOUND);
                });

        // 흔한 경우를 먼저 걸러 준다. 동시 요청은 저장 시점의 유니크 제약이 막는다.
        if (groupRoutineVerificationRepository.findByAssignmentId(assignment.getId()).isPresent()) {
            throw new VerificationException(VerificationErrorCode.ALREADY_VERIFIED);
        }

        GroupRoutineVerification saved = commandService.saveGroupRoutine(
                assignment, request.mediaKey(), request.content(), verifiedAt);

        // 시간대 밖이거나 이미 완료면 여기서 그룹 도메인의 예외가 난다.
        assignmentCommandService.completeAssignment(assignment.getId(), verifiedAt);

        return new VerificationResDTO.GroupRoutine(
                saved.getId(), assignment.getId(), saved.getImageUrl(),
                saved.getContent(), saved.getVerifiedAt());
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

        ZonedDateTime now = ZonedDateTime.now(TimeUtil.KST);
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
}
