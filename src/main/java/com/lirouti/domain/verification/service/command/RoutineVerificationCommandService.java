package com.lirouti.domain.verification.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.group.service.command.GroupRoutineAssignmentCommandService;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 루틴 인증의 저장 트랜잭션.
 *
 * <p>사진 검증(형식·바이트)은 이 밖에서 끝난다. 외부 API 호출이라 트랜잭션 안에서 부르면
 * 커넥션을 그 왕복 시간만큼 붙잡는다(service_convention). 여기서부터가 DB 작업이다.
 *
 * <p>챌린지 인증과 나란한 위치지만 <b>덮어쓰기를 허용하지 않는다.</b> 챌린지의 재인증은
 * "이미 통과한 인증의 사진 교체"인데 루틴에는 그럴 이유가 없다.
 *
 * <p><b>{@code AchievementProgressEvent} 발행.</b> 개인 루틴 인증이 이 트랜잭션에서
 * 저장되는 시점이 곧 achievement 도메인이 구독하는 "루틴 완료" 사건이다. 이벤트 발행은
 * 저장과 같은 트랜잭션 안에서 호출하되, achievement 쪽 리스너가
 * {@code TransactionPhase.AFTER_COMMIT} 이라 이 트랜잭션이 실제로 커밋된 뒤에만
 * 반영된다 — 인증 저장이 롤백되면 업적 진행도도 따라 롤백된다.
 *
 * <p>그룹 루틴 인증({@code saveGroupRoutineAndComplete})은 현재 이 이벤트를 발행하지
 * 않는다. "개인 체크 또는 사진 인증으로 루틴 첫 완료"라는 업적 조건 문구가 그룹 루틴
 * 완료까지 포함하는지 기획 확인이 필요해서, 확인 전까지는 개인 루틴만 반영한다.
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
    private final GroupValidationService groupValidationService;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 그룹 루틴 인증의 DB 구간을 하나의 트랜잭션으로 처리한다.
     * 탈퇴의 미완료 할당 삭제와 같은 Assignment 행을 잠가 먼저 확정된 요청을 우선한다.
     */
    @Transactional
    public GroupRoutineVerification verifyGroupRoutineAndComplete(
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

        return saveGroupRoutineAndComplete(assignment, mediaKey, content, verifiedAt);
    }

    /**
     * 그룹 루틴 인증을 저장하고 할당을 완료로 넘긴다. <b>둘은 한 트랜잭션이어야 한다.</b>
     *
     * <p>완료 판정 자체는 그룹 도메인이 소유한다 — 시간대 제약과 중복 완료 차단을 이미
     * 담고 있어 인증 쪽에서 다시 구현하면 규칙이 두 벌이 된다. 여기서는 그것을 부르기만 한다.
     *
     * <p><b>트랜잭션을 나누면 할당이 영영 완료되지 못하는 상태가 생긴다.</b> 저장이 먼저
     * 커밋된 뒤 완료 처리가 실패하면(수행 시간 밖 등) 인증 행만 남는다. 그 뒤에 다시
     * 인증하려 하면 이미 있는 인증 행 때문에 409 가 나고, 유니크 제약이 재저장도 막는다.
     * 결국 그 할당은 완료로 갈 방법이 없어진다.
     *
     * <p>한 트랜잭션으로 묶으면 완료 처리가 실패할 때 저장도 함께 되돌아가, 사용자가
     * 수행 시간 안에 다시 인증할 수 있다. 사진 검증은 외부 호출이라 이 밖에 남는다.
     */
    @Transactional
    public GroupRoutineVerification saveGroupRoutineAndComplete(
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

        // 같은 트랜잭션에 참여한다(REQUIRED). 여기서 예외가 나면 위 저장도 함께 롤백된다.
        assignmentCommandService.completeAssignmentAndRecordActivity(assignment, verifiedAt);
        return saved;
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
                routine.getCategory().getId()
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
