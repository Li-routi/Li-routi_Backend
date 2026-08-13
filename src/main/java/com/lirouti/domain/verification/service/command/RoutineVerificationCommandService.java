package com.lirouti.domain.verification.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 루틴 인증의 저장 트랜잭션.
 *
 * <p>사진 검증(형식·바이트)은 이 밖에서 끝난다. 외부 API 호출이라 트랜잭션 안에서 부르면
 * 커넥션을 그 왕복 시간만큼 붙잡는다(service_convention). 여기서부터가 DB 작업이다.
 *
 * <p>최초 인증과 명시적 재인증을 모두 이 트랜잭션 경계에서 처리한다. 재인증은 인증 행 ID를
 * 유지해 피드와 읽음 커서 참조를 보존하고, 연결된 interaction만 초기화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineVerificationCommandService {

    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;
    private final GroupValidationService groupValidationService;
    private final GroupRoutineVerificationLikeRepository groupRoutineVerificationLikeRepository;
    private final GroupRoutineVerificationDisappointmentRepository disappointmentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;

    /**
     * 그룹 루틴 인증의 DB 구간을 하나의 트랜잭션으로 처리한다.
     * 탈퇴의 미완료 할당 삭제와 같은 Assignment 행을 잠가 먼저 확정된 요청을 우선한다.
     */
    @Transactional
    public GroupRoutineVerification verifyGroupRoutine(
            Long memberId,
            Long groupId,
            Long routineId,
            LocalDate assignedDate,
            String mediaKey,
            String content,
            LocalDateTime verifiedAt
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

    /**
     * 그룹 루틴 인증을 저장한다. assignment의 최종 COMPLETED/MISSED 판정은 마감 batch가 맡는다.
     *
     * <p>수행 시간·상태 검증은 저장 전에 assignment 잠금 안에서 끝낸다. 사진 검증은 외부
     * 호출이라 이 밖에 남는다.
     */
    @Transactional
    public GroupRoutineVerification saveGroupRoutine(
            GroupRoutineAssignment assignment,
            String mediaKey,
            String content,
            LocalDateTime verifiedAt
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
        return verification;
    }

    @Transactional
    public MemberRoutineVerification saveMemberRoutine(
            MemberRoutine routine,
            String mediaKey,
            String content,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt
    ) {
        MemberRoutineVerification verification = MemberRoutineVerification.builder()
                .memberRoutine(routine)
                .verifiedDate(verifiedDate)
                .verifiedAt(verifiedAt)
                .imageUrl(mediaKey)
                .content(content)
                .build();
        return save(() -> memberRoutineVerificationRepository.saveAndFlush(verification));
    }

    /**
     * 유니크 제약 위반을 이 자리에서 잡아 409로 바꾼다.
     *
     * <p>"이미 인증했는지"를 먼저 조회해 판단하는 것만으로는 부족하다 — 조회와 저장 사이에
     * 같은 요청이 두 번 들어오면 둘 다 통과한다. 앞선 조회는 흔한 경우를 빨리 걸러 주는 것이고,
     * 마지막 방어는 DB 제약이다(챌린지 인증과 같은 방식).
     *
     * <p>두 예외를 모두 잡는 이유는, 같은 유니크 키로 INSERT 가 겹칠 때 InnoDB 가 중복 키
     * 오류 대신 데드락으로 판정해 CannotAcquireLockException 을 줄 수 있기 때문이다.
     */
    private <T> T save(java.util.function.Supplier<T> persist) {
        try {
            return persist.get();
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            throw new VerificationException(VerificationErrorCode.VERIFICATION_CONFLICT);
        }
    }
}
