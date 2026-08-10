package com.lirouti.domain.wallet.service;

import com.lirouti.domain.wallet.service.command.WalletCommandService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인이 재화를 움직일 때 부르는 입구.
 *
 * <p><b>일부러 {@code @Transactional} 이 없다.</b> 제약 위반이 나면 그 트랜잭션은 이미 롤백
 * 대상이라 안에서는 되살릴 수 없다. 트랜잭션 밖에서 잡아야 다시 시도하거나 이미 처리된 결과를
 * 돌려줄 수 있다. 같은 이유로 인증 저장도 오케스트레이터와 쓰기 빈이 나뉘어 있다.
 *
 * <p>제약 위반은 두 군데서 난다.
 * <ol>
 *   <li><b>멱등 키 충돌</b> — 같은 요청이 동시에 두 번 들어왔다. 먼저 쓴 쪽의 거래를 돌려주면
 *       호출부는 한 번 처리된 것으로 본다. <b>이것이 멱등의 정의다.</b></li>
 *   <li><b>지갑 생성 충돌</b> — 첫 거래가 동시에 둘 들어와 둘 다 지갑을 만들려 했다. 진 쪽은
 *       다시 시도하면 이미 생긴 지갑을 잠그고 정상 진행한다.</li>
 * </ol>
 *
 * <p>둘을 구분하지 않고 <b>한 번 다시 시도한 뒤, 그래도 안 되면 멱등 조회로 확인</b>한다.
 * 예외 메시지로 원인을 가르는 방식은 DB·드라이버 버전에 묶여 조용히 깨진다.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletCommandService walletCommandService;

    public WalletResult grant(WalletCommand command, int paidAmount, int freeAmount) {
        try {
            return walletCommandService.grant(command, paidAmount, freeAmount);
        } catch (DataIntegrityViolationException e) {
            return recover(command, e, () -> walletCommandService.grant(command, paidAmount, freeAmount));
        }
    }

    public WalletResult deduct(WalletCommand command, int amount) {
        try {
            return walletCommandService.deduct(command, amount);
        } catch (DataIntegrityViolationException e) {
            return recover(command, e, () -> walletCommandService.deduct(command, amount));
        }
    }

    /**
     * 이미 처리된 요청이면 그 결과를, 지갑 생성 경합이었으면 재시도 결과를 돌려준다.
     *
     * <p>멱등 조회를 먼저 하는 이유는 <b>재시도가 안전하지 않은 경우가 있기 때문</b>이다.
     * 먼저 쓴 쪽이 이미 잔액을 움직였는데 다시 시도하면 두 번 반영된다. 키가 이미 있다면
     * 그것이 답이다.
     */
    private WalletResult recover(
            WalletCommand command,
            DataIntegrityViolationException original,
            java.util.function.Supplier<WalletResult> retry
    ) {
        return walletCommandService.replayOf(command)
                .orElseGet(() -> {
                    try {
                        return retry.get();
                    } catch (DataIntegrityViolationException retryFailure) {
                        // 두 번 연달아 깨지는 것은 경합으로 설명되지 않는다. 삼키면 잔액이
                        // 안 움직인 채 성공으로 보이므로 원래 예외를 그대로 올린다.
                        throw original;
                    }
                });
    }
}
