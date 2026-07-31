package com.lirouti.domain.verification.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineVerificationCommandService {

    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;

    /**
     * 그룹 루틴 인증을 저장한다.
     *
     * <p>완료 처리는 이 메서드가 하지 않는다. 할당의 상태 전이는 그룹 도메인이 소유하며
     * 시간대 제약·중복 완료 차단을 이미 담고 있다. 호출부가 이 저장 뒤에 그쪽을 부른다.
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
        return save(() -> groupRoutineVerificationRepository.saveAndFlush(verification));
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
