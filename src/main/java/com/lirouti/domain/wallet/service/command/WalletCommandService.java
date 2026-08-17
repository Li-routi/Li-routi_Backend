package com.lirouti.domain.wallet.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.entity.WalletTransaction;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.exception.WalletException;
import com.lirouti.domain.wallet.exception.code.error.WalletErrorCode;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import com.lirouti.domain.wallet.repository.WalletTransactionRepository;
import com.lirouti.domain.wallet.service.WalletResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 잔액을 실제로 움직이는 자리. <b>트랜잭션 경계가 여기에 있다.</b>
 *
 * <p>지갑 잠금 · 잔액 갱신 · 원장 기록이 한 트랜잭션 안에서 끝나야 한다. 하나라도 밖으로
 * 나가면 잔액과 원장이 서로 다른 말을 하는 순간이 생긴다.
 *
 * <p>멱등 충돌과 지갑 생성 충돌의 <b>복구</b>는 여기서 하지 않는다 — 제약 위반이 나면 이
 * 트랜잭션은 이미 롤백 대상이라 같은 트랜잭션 안에서 되살릴 수 없다. 그 처리는 트랜잭션
 * 밖에 있는 {@code WalletService} 가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class WalletCommandService {

    private final MemberWalletRepository memberWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final MemberRepository memberRepository;

    /**
     * 지급. 유상·무상을 나눠 받는다 — 충전은 결제분과 보너스가 함께 들어오므로 한 번의
     * 지급이 양쪽을 동시에 올릴 수 있다.
     */
    @Transactional
    public WalletResult grant(WalletCommand command, int paidAmount, int freeAmount) {
        if (paidAmount < 0 || freeAmount < 0 || paidAmount + freeAmount <= 0) {
            throw new WalletException(WalletErrorCode.INVALID_AMOUNT);
        }
        // 지갑 행을 만들거나 잠그기 전에 막는다. 엔티티에도 같은 검사가 있지만 그것은
        // IllegalArgumentException 이라 응답이 500 이 된다 — 호출부의 잘못을 400 으로 알린다.
        if (paidAmount > 0 && !command.currency().isPaidBalanceAllowed()) {
            throw new WalletException(WalletErrorCode.PAID_BALANCE_NOT_ALLOWED);
        }
        Optional<WalletResult> already = replayOf(command);
        if (already.isPresent()) {
            return already.get();
        }

        MemberWallet wallet = lockOrCreate(command.memberId(), command.currency());
        wallet.grant(paidAmount, freeAmount);
        return toResult(wallet, record(wallet, command, paidAmount, freeAmount), true);
    }

    /**
     * 차감. 무상부터 쓰고 모자란 만큼만 유상에서 뺀다.
     *
     * <p>잔액이 모자라면 아무것도 바꾸지 않고 예외를 던진다 — 부분 차감은 없다.
     */
    @Transactional
    public WalletResult deduct(WalletCommand command, int amount) {
        if (amount <= 0) {
            throw new WalletException(WalletErrorCode.INVALID_AMOUNT);
        }
        Optional<WalletResult> already = replayOf(command);
        if (already.isPresent()) {
            return already.get();
        }

        MemberWallet wallet = lockOrCreate(command.memberId(), command.currency());
        if (!wallet.canAfford(amount)) {
            throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }
        MemberWallet.Deduction deduction = wallet.deduct(amount);
        return toResult(wallet,
                record(wallet, command, -deduction.fromPaid(), -deduction.fromFree()), true);
    }

    /**
     * 이미 처리한 요청인지 본다. <b>회원으로 좁혀 찾는다</b> — 키만으로 찾으면 같은 키를 쓴
     * 다른 사람의 거래를 집어 와, 이 회원의 잔액은 그대로인 채 성공으로 답하게 된다.
     *
     * <p>되돌려줄 때도 <b>지금 잔액</b>을 싣는다. 거래 당시 잔액을 실으면 그 뒤에 일어난
     * 다른 거래가 반영되지 않아, 호출부가 화면에 옛 숫자를 그린다.
     */
    @Transactional(readOnly = true)
    public Optional<WalletResult> replayOf(WalletCommand command) {
        return walletTransactionRepository
                .findByMemberIdAndCurrencyAndIdempotencyKey(
                        command.memberId(), command.currency(), command.idempotencyKey())
                .map(tx -> requireSameOperation(tx, command))
                .map(tx -> memberWalletRepository
                        .findByMemberIdAndCurrency(command.memberId(), command.currency())
                        .map(wallet -> toResult(wallet, tx, false))
                        // 거래가 있는데 지갑이 없을 수는 없다. 그래도 값이 없으면 거래 당시
                        // 잔액으로 답한다 — 예외를 던지면 멱등이 실패로 뒤집힌다.
                        .orElseGet(() -> new WalletResult(tx.getId(), command.currency(),
                                tx.getPaidBalanceAfter(), tx.getFreeBalanceAfter(), false)));
    }

    /**
     * 되돌려주기 전에 <b>같은 사건이 맞는지</b> 확인한다.
     *
     * <p>회원·재화가 같은데 거래 종류가 다르면, 호출부가 서로 다른 일에 같은 키를 붙인 것이다.
     * 그대로 되돌려주면 <b>차감이 안 됐는데 성공으로 보인다</b> — 조용히 돈이 새는 쪽이라
     * 시끄럽게 실패시킨다.
     */
    private WalletTransaction requireSameOperation(WalletTransaction found, WalletCommand command) {
        if (found.getTransactionType() != command.transactionType()) {
            throw new WalletException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return found;
    }

    private WalletResult toResult(MemberWallet wallet, WalletTransaction tx, boolean applied) {
        return new WalletResult(tx.getId(), wallet.getCurrency(),
                wallet.getPaidBalance(), wallet.getFreeBalance(), applied);
    }

    /**
     * 지갑 행을 잠근 채로 가져온다. 없으면 만든다.
     *
     * <p>가입 시점에 미리 만들지 않는 이유는 둘이다 — 재화가 하나 늘 때마다 기존 회원 전부에게
     * 백필해야 하고, 가입 흐름(다른 도메인)을 건드려야 한다.
     *
     * <p>만드는 순간에는 잠글 행이 없으므로 <b>동시에 두 요청이 만들 수 있다.</b> 그것은
     * 유니크 제약이 막고, 진 쪽은 트랜잭션 밖에서 한 번 다시 시도한다.
     */
    private MemberWallet lockOrCreate(Long memberId, Currency currency) {
        return memberWalletRepository.findForUpdate(memberId, currency)
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new WalletException(WalletErrorCode.MEMBER_NOT_FOUND));
                    return memberWalletRepository.saveAndFlush(
                            MemberWallet.builder().member(member).currency(currency).build());
                });
    }

    private WalletTransaction record(
            MemberWallet wallet,
            WalletCommand command,
            int paidDelta,
            int freeDelta
    ) {
        return walletTransactionRepository.saveAndFlush(WalletTransaction.builder()
                .member(wallet.getMember())
                .currency(wallet.getCurrency())
                .transactionType(command.transactionType())
                .paidDelta(paidDelta)
                .freeDelta(freeDelta)
                .paidBalanceAfter(wallet.getPaidBalance())
                .freeBalanceAfter(wallet.getFreeBalance())
                .idempotencyKey(command.idempotencyKey())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .build());
    }

    /**
     * 거래 한 건의 공통 입력.
     *
     * <p>인자를 늘어놓지 않고 묶는 이유는 {@code Long}·{@code String} 이 연달아 오면 호출부에서
     * 순서가 바뀌어도 컴파일이 통과하기 때문이다. 재화가 오가는 자리에서 그 실수는 비싸다.
     *
     * @param idempotencyKey 호출부가 뜻이 담기게 만든다. 같은 사건에는 같은 키가 나와야
     *                       재시도가 두 번 반영되지 않는다.
     */
    public record WalletCommand(
            Long memberId,
            Currency currency,
            WalletTransactionType transactionType,
            String idempotencyKey,
            String referenceType,
            Long referenceId
    ) {
    }
}
