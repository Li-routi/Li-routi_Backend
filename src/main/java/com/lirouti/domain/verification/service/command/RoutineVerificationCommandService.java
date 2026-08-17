package com.lirouti.domain.verification.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

/**
 * 루틴 인증의 저장 트랜잭션.
 *
 * <p>사진 검증(형식·바이트)은 이 밖에서 끝난다. 외부 API 호출이라 트랜잭션 안에서 부르면
 * 커넥션을 그 왕복 시간만큼 붙잡는다(service_convention). 여기서부터가 DB 작업이다.
 *
 * <p>최초 인증과 명시적 재인증을 모두 이 트랜잭션 경계에서 처리한다. 재인증은 인증 행 ID를
 * 유지해 피드와 읽음 커서 참조를 보존하고, 연결된 interaction만 초기화한다.
 *
 * <p><b>{@code AchievementProgressEvent} 발행.</b> 개인 루틴 인증이 이 트랜잭션에서
 * 저장되는 시점이 곧 achievement 도메인이 구독하는 "루틴 완료" 사건이다. 이벤트 발행은
 * 저장과 같은 트랜잭션 안에서 호출하되, achievement 쪽 리스너가
 * {@code TransactionPhase.AFTER_COMMIT} 이라 이 트랜잭션이 실제로 커밋된 뒤에만
 * 반영된다 — 인증 저장이 롤백되면 업적 진행도도 따라 롤백된다.
 *
 * <p>그룹 루틴은 인증 저장만으로 완료가 되지 않는다. 마감 batch가 Like 기준을 만족한
 * Assignment를 COMPLETED로 전이할 때 그룹 업적과 그룹 스트릭을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineVerificationCommandService {

    /** achievement 도메인이 구독하는 루틴 완료 이벤트의 condition key */
    private static final String CONDITION_KEY_ROUTINE_COMPLETE_COUNT = "ROUTINE_COMPLETE_COUNT";
    private static final String SOURCE_TYPE_MEMBER_ROUTINE_VERIFICATION = "MEMBER_ROUTINE_VERIFICATION";

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

        // 그룹 인증도 활동일을 만든다 -- 해금은 "앱을 꾸준히 썼는가" 에 대한 보상이지 특정
        // 기능을 밀어주는 장치가 아니다. all_completed 는 개인 루틴만 보는 값이라 여기서
        // 올리지 않는다(이미 1 인 날을 덮어 내리지도 않는다).
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

        // 재인증은 활동일을 남기지 않는다. 그 할당의 인증은 이미 있었고 사진을 바꾸는 것이라
        // 새로운 완료가 아니다 -- 남기면 어제 인증의 사진만 오늘 교체해도 오늘이 활동일이 되어
        // "며칠 했는가" 가 실제로 한 날보다 부풀어 오른다. 챌린지 재인증도 같은 규칙이다.
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
        MemberRoutineVerification saved = save(() -> memberRoutineVerificationRepository.saveAndFlush(verification));
        publishRoutineCompleteEvent(routine, saved);

        memberRoutineStreakCommandService.recordCompletion(
                routine.getMember().getId(), verifiedDate, verifiedAt,
                SOURCE_TYPE_MEMBER_ROUTINE_VERIFICATION, saved.getId());

        // 활동일은 인증과 같은 트랜잭션에서 남긴다. 나누면 인증은 있는데 활동일이 없는 날이
        // 생기고, 그 하루는 어떤 조건에도 세어지지 않는다.
        //
        // "예정된 것을 전부 했는가" 도 여기서 판정한다 -- 과거에 무엇이 예정돼 있었는지는
        // 나중에 복원할 수 없어(요일 행이 물리 삭제된다) 지금이 유일한 시점이다.
        memberActivityDayCommandService.recordWithCompletion(
                routine.getMember().getId(), verifiedDate);
        // 활동일을 남긴 직후에 판정한다. 방금 것까지 세어야 "오늘 채운" 조건이 오늘 열린다.
        characterUnlockCommandService.evaluateAndUnlock(routine.getMember().getId());
        return saved;
    }

    /**
     * 개인 루틴 완료를 achievement 도메인에 알린다.
     *
     * <p>{@code sourceId} 로 이번에 저장된 인증 행의 id를 쓴다 — 한 회원이 같은 루틴을
     * 같은 날 두 번 인증할 수 없으므로(유니크 제약) 이 값은 자연히 "그 회원의 그날 그
     * 루틴 완료 1건"을 가리키는 안정적인 키다.
     *
     * <p>{@code routine.getCategory().getId()} 는 지연 로딩 프록시라도 FK 값이라 별도
     * 쿼리 없이 읽힌다 — 프록시 초기화(실제 엔티티 로딩)가 필요한 건 id 외의 필드에 접근할
     * 때뿐이다.
     */
    private void publishRoutineCompleteEvent(MemberRoutine routine, MemberRoutineVerification saved) {
        if (eventPublisher == null) {
            return; // 단위 테스트가 이 서비스를 직접 생성한 경로 - 알림 발행과 같은 가드
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
