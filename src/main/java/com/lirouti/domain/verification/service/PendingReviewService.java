package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.client.AnthropicVerificationReviewClient;
import com.lirouti.domain.challenge.client.VerificationReview;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.service.MediaImage;
import com.lirouti.domain.media.service.MediaImageLoad;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.verification.service.command.PendingReviewCommandService;
import com.lirouti.global.properties.AiReviewProperties;
import com.lirouti.global.properties.PendingReviewProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 보류된 인증을 다시 심사한다.
 *
 * <h3>이 클래스에 {@code @Transactional} 이 없는 것은 의도다</h3>
 * 재심사는 S3 와 AI 를 부른다. 트랜잭션 안에서 부르면 DB 커넥션과 행 락을 그 왕복 시간만큼
 * 붙잡는다(service_convention). DB 변경은 {@link PendingReviewCommandService} 가 맡고,
 * 여기서는 외부 호출과 순서만 다룬다.
 *
 * <h3>유예가 끝나면 통과시킨다</h3>
 * 반려하지 않는다 — 사용자가 잘못한 것이 없는데 남의 장애로 반려하는 것은 부당하다.
 * 무기한 보류하지도 않는다 — 사진이 영영 안 보이는 것도 같은 이유로 부당하다.
 * <b>결과는 지금과 같고 시점만 늦춰진다.</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingReviewService {
    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final PendingReviewCommandService pendingReviewCommandService;
    private final AnthropicVerificationReviewClient reviewClient;
    private final MediaService mediaService;
    private final AiReviewProperties aiReviewProperties;
    private final PendingReviewProperties pendingReviewProperties;
    private final Clock clock;

    /**
     * 보류 건을 한 번 훑는다.
     *
     * <p>한 건이 실패해도 나머지를 계속 본다. 하나 때문에 그 실행 전체가 멈추면, 문제 건이
     * 앞자리에 있는 동안 뒤의 건들이 상한까지 방치된다.
     */
    public void sweepPending() {
        if (!pendingReviewProperties.isEnabled()) {
            log.debug("재심사가 꺼져 있어 건너뜁니다.");
            return;
        }

        List<ChallengeVerification> pending = challengeVerificationRepository
                .findPendingOldestFirst(PageRequest.of(0, pendingReviewProperties.getBatchSize()));
        if (pending.isEmpty()) {
            return;
        }

        log.info("보류 인증 재심사를 시작합니다. 대상={}건", pending.size());
        int approved = 0;
        int rejected = 0;
        int held = 0;

        for (ChallengeVerification verification : pending) {
            try {
                switch (process(verification)) {
                    case APPROVED -> approved++;
                    case REJECTED -> rejected++;
                    default -> held++;
                }
            } catch (RuntimeException e) {
                held++;
                log.warn("보류 건 재심사에 실패했습니다. 다음 주기에 다시 봅니다. verificationId={}",
                        verification.getId(), e);
            }
        }

        log.info("보류 인증 재심사를 마쳤습니다. 통과={}건, 반려={}건, 보류 유지={}건",
                approved, rejected, held);
    }

    /** 처리 결과. 로그 집계용이라 밖으로 내보내지 않는다. */
    private enum Outcome { APPROVED, REJECTED, HELD }

    private Outcome process(ChallengeVerification verification) {
        // 상한에 닿았으면 심사를 더 부르지 않는다. 어차피 통과시킬 것이라 호출이 낭비다.
        if (isExpired(verification)) {
            log.warn("보류 상한에 닿아 심사 없이 통과시킵니다."
                            + " verificationId={}, 보류={}시간, 시도={}회",
                    verification.getId(), hoursPending(verification), verification.getReviewAttempts());
            return promoteAndApprove(verification) ? Outcome.APPROVED : Outcome.HELD;
        }

        // 심사를 부르기 전에 시도를 올린다. 심사 도중 프로세스가 죽어도 시도가 사라지지 않는다.
        pendingReviewCommandService.recordAttempt(verification.getId());

        VerificationReview review = review(verification);
        if (review.shouldHold()) {
            return Outcome.HELD;
        }
        if (review.rejected()) {
            // 사진부터 지운다. 행을 먼저 지우면 그 사이 프로세스가 죽었을 때 대기본을 가리키는
            // 것이 아무것도 없어져, 수명 주기가 가져갈 때까지 반려 사진이 남는다.
            mediaService.deleteQuietly(verification.getImageUrl());
            pendingReviewCommandService.reject(verification.getId());
            return Outcome.REJECTED;
        }
        return promoteAndApprove(verification) ? Outcome.APPROVED : Outcome.HELD;
    }

    /**
     * 대기본을 공개 prefix 로 옮기고 행을 통과로 확정한다.
     *
     * <p>순서는 저장 경로와 같다 — 복사 → 확정 → 대기본 삭제. 복사가 실패하면 보류를 유지하고
     * 다음 주기에 다시 본다. <b>사진이 사라진 채 공개로 바뀌는 방향으로는 실패하지 않는다.</b>
     */
    private boolean promoteAndApprove(ChallengeVerification verification) {
        String stagingKey = verification.getImageUrl();
        String publicKey;
        try {
            // 심사한 ETag 를 특정할 수 없다. 최초 심사가 답을 못 줬거나 상한으로 건너뛴 경우라
            // 비교할 값이 없다. promote 가 현재 ETag 로 조건을 걸어 조회-복사 사이의 교체만 막는다.
            publicKey = mediaService.promote(stagingKey, MediaPurpose.CHALLENGE_VERIFICATION, null);
        } catch (MediaException e) {
            log.warn("보류 건을 공개 prefix 로 옮기지 못해 보류를 유지합니다. verificationId={}",
                    verification.getId(), e);
            return false;
        }

        pendingReviewCommandService.approve(verification.getId(), publicKey);
        mediaService.deleteQuietly(stagingKey);

        log.info("보류가 풀려 인증을 공개했습니다. verificationId={}, 보류={}시간, 시도={}회",
                verification.getId(), hoursPending(verification), verification.getReviewAttempts());
        return true;
    }

    private VerificationReview review(ChallengeVerification verification) {
        if (!aiReviewProperties.isEnabled()) {
            // 그 사이 킬 스위치를 껐다. 장애가 아니므로 통과시킨다.
            return VerificationReview.disabled();
        }
        Challenge challenge = verification.getMemberChallenge().getChallenge();

        MediaImageLoad load = mediaService.loadForReview(
                verification.getImageUrl(), aiReviewProperties.getMaxImageDimension());
        MediaImage image = load.image();
        if (image == null) {
            return load.failure() == MediaImageLoad.Failure.TOO_LARGE
                    ? VerificationReview.notApplicable("사진이 심사 상한을 넘음")
                    : VerificationReview.transientFailure("심사용 사진을 읽지 못함");
        }
        return reviewClient.review(challenge.getName(), challenge.getDescription(), image);
    }

    /**
     * 상한에 닿았는가. <b>시간과 횟수 중 먼저 닿는 쪽</b>이다.
     *
     * <p>둘 다 두는 이유는 스케줄러가 멈춰 있던 구간을 시간이 받아 주기 때문이다. 횟수만 두면
     * 스케줄러가 죽어 있는 동안 보류가 영영 안 풀린다.
     */
    private boolean isExpired(ChallengeVerification verification) {
        if (verification.getReviewAttempts() >= pendingReviewProperties.getMaxAttempts()) {
            return true;
        }
        LocalDateTime since = verification.getPendingSince();
        return since != null
                && Duration.between(since, LocalDateTime.now(clock))
                        .compareTo(pendingReviewProperties.getMaxAge()) >= 0;
    }

    private long hoursPending(ChallengeVerification verification) {
        LocalDateTime since = verification.getPendingSince();
        return since == null ? 0 : Duration.between(since, LocalDateTime.now(clock)).toHours();
    }

    /** 보류 건수. 0 이면 남기지 않는다 — 평소에 조용해야 이상할 때 눈에 띈다. */
    public void logPendingCount() {
        long count = challengeVerificationRepository.countByReviewStatus(ReviewStatus.PENDING);
        if (count > 0) {
            log.warn("심사 보류 인증이 쌓여 있습니다. 건수={}", count);
        }
    }
}
