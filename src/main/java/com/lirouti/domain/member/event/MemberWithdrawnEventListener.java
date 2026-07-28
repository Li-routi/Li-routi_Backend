package com.lirouti.domain.member.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.lirouti.domain.auth.service.TokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberWithdrawnEventListener {
    private final TokenService tokenService;

    // 트랜잭션이 커밋된 후에 이벤트를 발행하도록 설정
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemberWithdrawnEvent event) {
        try {
            tokenService.logout(event.accessToken());
            log.info("회원 탈퇴 후 토큰 폐기를 완료했습니다. memberId={}", event.memberId());
        } catch (RuntimeException e) {
            log.error("회원 탈퇴 후 토큰 폐기에 실패했습니다. memberId={}", event.memberId(), e);
        }
    }
}
