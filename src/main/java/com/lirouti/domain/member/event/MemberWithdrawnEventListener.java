package com.lirouti.domain.member.event;

import com.lirouti.domain.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberWithdrawnEventListener {
    private final TokenService tokenService;

    // 트랜잭션이 커밋된 후에 이벤트를 발행하도록 설정
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(
        retryFor = { RedisConnectionFailureException.class, RedisSystemException.class }, // 어떤 예외를 재시도할지 명시
        maxAttempts = 3, // 최대 재시도 횟수를 지정
        backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000)
    )
    public void handle(MemberWithdrawnEvent event) {
        tokenService.logout(event.accessToken());
        log.info("회원 탈퇴 후 토큰 폐기를 완료했습니다. memberId={}", event.memberId());
    }

    @Recover
    public void recover(RuntimeException e, MemberWithdrawnEvent event) {
        log.error("회원 탈퇴 후 토큰 폐기에 실패했습니다. 재시도 횟수를 초과했습니다. memberId={}", event.memberId(), e);
    }
}
