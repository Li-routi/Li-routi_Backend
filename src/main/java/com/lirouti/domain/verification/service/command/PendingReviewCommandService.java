package com.lirouti.domain.verification.service.command;

import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;

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
    private final ApplicationEventPublisher eventPublisher;

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
    public boolean approve(Long verificationId, String reviewedKey, String publicKey) {
        return challengeVerificationRepository.findById(verificationId)
                .filter(ChallengeVerification::isPending)
                // 심사를 시작할 때 보던 그 사진이어야 한다. 그 사이 당일 재인증이 들어오면
                // 행의 사진이 새것으로 바뀌는데, 그대로 확정하면 옛 사진의 판정으로 새 사진을
                // 공개하게 된다. 어긋나면 아무것도 하지 않는다 — 새 사진은 자기 판정을 따른다.
                .filter(v -> reviewedKey.equals(v.getImageUrl()))
                .map(v -> {
                    v.approveWith(publicKey);
                    eventPublisher.publishEvent(reviewNotification(v, true));
                    return true;
                })
                .orElseGet(() -> {
                    log.info("그 사이 사진이 바뀌어 승격 결과를 버립니다. verificationId={}, 심사한 key={}",
                            verificationId, reviewedKey);
                    return false;
                });
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
    public boolean reject(Long verificationId, String reviewedKey) {
        ChallengeVerification verification = challengeVerificationRepository.findById(verificationId)
                .filter(ChallengeVerification::isPending)
                // 승격과 같은 이유다. 그 사이 재인증이 들어왔으면 옛 사진의 반려로 새 사진을
                // 지우게 된다 — 사용자가 방금 올린 멀쩡한 사진이 사라진다.
                .filter(v -> reviewedKey.equals(v.getImageUrl()))
                .orElse(null);
        if (verification == null) {
            log.info("그 사이 사진이 바뀌어 반려 결과를 버립니다. verificationId={}, 심사한 key={}",
                    verificationId, reviewedKey);
            return false;
        }

        MemberChallenge unlocked = verification.getMemberChallenge();
        Long memberId = unlocked.getMember().getId();
        Long challengeId = unlocked.getChallenge().getId();
        Integer round = verification.getParticipationRound();
        NotificationRequestedEvent rejectedNotification = reviewNotification(verification, false);

        // 참여 행을 먼저 잠근다. 저장 경로가 같은 순서로 잠그므로(참여 → 인증), 여기서
        // 뒤집으면 반려 확정과 당일 재인증이 서로의 잠금을 기다리다 데드락이 된다.
        MemberChallenge locked = memberChallengeRepository
                .findByMemberIdAndChallengeIdForUpdate(memberId, challengeId)
                .orElse(null);

        challengeVerificationRepository.delete(verification);
        // 지운 행이 아래 재계산 조회에 잡히지 않도록 먼저 반영한다.
        challengeVerificationRepository.flush();

        // 지난 회차의 인증을 지운 것이면 지금 스트릭과 무관하다. 그 회차 날짜로 다시 세면
        // 오히려 현재 회차의 스트릭을 옛 기록으로 덮어쓴다.
        if (locked != null && round.equals(locked.getParticipationRound())) {
            List<LocalDate> approved = challengeVerificationRepository
                    .findApprovedDatesInRound(locked.getId(), round);
            locked.recalculateStreak(approved);
        }

        log.info("보류가 반려로 확정돼 인증을 지웠습니다. verificationId={}, memberId={}, challengeId={}",
                verificationId, memberId, challengeId);
        eventPublisher.publishEvent(rejectedNotification);
        return true;
    }

    /** 대기 인증 처리 결과를 인증 작성자에게 전달할 이벤트로 만든다. */
    private NotificationRequestedEvent reviewNotification(
            ChallengeVerification verification,
            boolean approved
    ) {
        Long memberId = verification.getMemberChallenge().getMember().getId();
        Long challengeId = verification.getMemberChallenge().getChallenge().getId();
        return new NotificationRequestedEvent(
                memberId,
                NotificationCategory.CHALLENGE,
                approved ? NotificationType.CHALLENGE_REVIEW_APPROVED
                        : NotificationType.CHALLENGE_REVIEW_REJECTED,
                approved ? "대기 중이던 인증이 승인됐어요" : "대기 중이던 인증을 다시 확인해 주세요",
                approved ? "챌린지 인증이 공개됐어요." : "챌린지 인증이 반려됐어요. 다시 인증할 수 있어요.",
                null,
                verification.getId(),
                "CHALLENGE_VERIFICATION",
                "challenge-review:" + verification.getId() + ":" + (approved ? "approved" : "rejected")
        );
    }
}
