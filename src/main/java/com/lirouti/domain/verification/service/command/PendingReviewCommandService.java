package com.lirouti.domain.verification.service.command;

import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 보류 건의 <b>DB 변경만</b> 담당한다. 트랜잭션 경계가 이 클래스에 있다.
 *
 * <p>{@link com.lirouti.domain.verification.service.PendingReviewService} 에서 분리한 이유는
 * 저장 경로와 같다 — 재심사는 AI 와 S3 를 부르는데, 그것을 트랜잭션 안에서 하면 DB 커넥션과
 * 행 락을 외부 왕복 시간만큼 붙잡는다(service_convention). 같은 클래스에서 메서드만 나누면
 * 자기 호출이 프록시를 거치지 않아 트랜잭션이 걸리지 않으므로 빈을 나눴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingReviewCommandService {
    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final MemberChallengeRepository memberChallengeRepository;

    /**
     * 재심사를 한 번 시도했다고 기록한다. <b>심사를 부르기 전에</b> 올린다.
     *
     * <p>성공·실패와 무관하게 올리는 이유는, 실패할 때만 올리면 계속 죽는 호출이 상한에 영영
     * 닿지 않기 때문이다. 심사 뒤에 올리면 심사 도중 프로세스가 죽었을 때 시도가 사라진다.
     */
    @Transactional
    public void recordAttempt(Long verificationId) {
        challengeVerificationRepository.findById(verificationId)
                .filter(ChallengeVerification::isPending)
                .ifPresent(ChallengeVerification::recordReviewAttempt);
    }

    /**
     * 보류를 풀어 공개한다. 재심사가 통과했거나 상한에 닿아 통과시킨 경우다.
     *
     * <p>사진은 이미 공개 prefix 로 옮겨져 있고, 여기서는 <b>그 key 를 행에 심는다.</b>
     * 스트릭은 건드리지 않는다 — 보류 시점에 이미 올려 뒀다.
     */
    @Transactional
    public void approve(Long verificationId, String publicKey) {
        challengeVerificationRepository.findById(verificationId)
                .filter(ChallengeVerification::isPending)
                .ifPresent(v -> v.approveWith(publicKey));
    }

    /**
     * 보류가 반려로 확정됐다. <b>오늘 반려와 같은 결과로 만든다</b> — 행을 지운다.
     *
     * <p>반려 행을 남기지 않는 이유는 유니크 제약 {@code (참여, 회차, 날짜)} 이 그날의 재시도를
     * 막기 때문이다. 사용자는 다시 찍어 올릴 수 있어야 한다.
     *
     * <p><b>스트릭을 다시 센다.</b> 1 을 빼거나 날짜를 되돌리면 틀린 값이 남는다 — 이 인증
     * 뒤에 다른 인증이 붙어 있었다면 단순 감산은 그것까지 지운 셈이 된다.
     *
     * <p>잠금은 저장 경로와 같은 것을 쓴다. 재계산과 인증 저장이 같은 행을 건드리므로, 다른
     * 잠금을 쓰면 둘이 서로를 덮어쓴다.
     */
    @Transactional
    public void reject(Long verificationId) {
        ChallengeVerification verification = challengeVerificationRepository.findById(verificationId)
                .filter(ChallengeVerification::isPending)
                .orElse(null);
        if (verification == null) {
            return;
        }

        MemberChallenge unlocked = verification.getMemberChallenge();
        Long memberId = unlocked.getMember().getId();
        Long challengeId = unlocked.getChallenge().getId();
        Integer round = verification.getParticipationRound();

        challengeVerificationRepository.delete(verification);
        // 지운 행이 아래 재계산 조회에 잡히지 않도록 먼저 반영한다.
        challengeVerificationRepository.flush();

        memberChallengeRepository.findByMemberIdAndChallengeIdForUpdate(memberId, challengeId)
                .ifPresent(locked -> {
                    // 지난 회차의 인증을 지운 것이면 지금 스트릭과 무관하다. 그 회차 날짜로
                    // 다시 세면 오히려 현재 회차의 스트릭을 옛 기록으로 덮어쓴다.
                    if (!round.equals(locked.getParticipationRound())) {
                        return;
                    }
                    List<LocalDate> approved = challengeVerificationRepository
                            .findApprovedDatesInRound(locked.getId(), round);
                    locked.recalculateStreak(approved);
                });

        log.info("보류가 반려로 확정돼 인증을 지웠습니다. verificationId={}, memberId={}, challengeId={}",
                verificationId, memberId, challengeId);
    }
}
