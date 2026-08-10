package com.lirouti.domain.reward.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.reward.entity.RewardGrant;
import com.lirouti.domain.reward.enums.RewardReason;
import com.lirouti.domain.reward.exception.RewardClawbackException;
import com.lirouti.domain.reward.repository.RewardGrantRepository;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.domain.wallet.service.command.WalletCommandService;
import com.lirouti.domain.wallet.service.command.WalletCommandService.WalletCommand;
import com.lirouti.domain.wallet.dto.response.WalletResDTO;
import com.lirouti.domain.wallet.service.query.WalletQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 인증 통과 리워드의 지급과 회수.
 *
 * <p><b>메서드에 {@code @Transactional} 을 두지만 새 트랜잭션을 열지 않는다.</b> 호출부(인증
 * 저장·삭제)의 트랜잭션에 참여해야 한다 — 지급이 인증 저장과 갈리면 "인증은 저장됐는데
 * 리워드가 없는" 상태가 조용히 남고, 회수가 삭제와 갈리면 재화만 빼앗기고 글은 남는다.
 *
 * <p>지갑도 {@link WalletCommandService} 를 직접 부른다. 오케스트레이터({@code WalletService})는
 * 제약 위반을 트랜잭션 밖에서 복구하는 것이 일인데, 여기서는 위반이 나면 <b>인증 저장·삭제까지
 * 함께 되돌아가는 것이 맞다.</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardCommandService {

    /**
     * 챌린지 리워드는 <b>주황 보석({@link Currency#TOPAZ})</b>이다 — 무료 재화다.
     *
     * <p>파란 보석({@link Currency#GEM})은 현금으로만 얻는 유료 재화라 여기서 주면
     * <b>인증만 하면 유료 재화가 공짜로 생긴다.</b> 실제로 그렇게 들어갔다가 되돌린 자리다.
     */
    private static final Currency REWARD_CURRENCY = Currency.TOPAZ;

    private final RewardGrantRepository rewardGrantRepository;
    private final WalletCommandService walletCommandService;
    private final WalletQueryService walletQueryService;

    /**
     * 인증 한 건에 리워드를 준다.
     *
     * <p><b>무상 잔액으로 넣는다.</b> 현금으로 산 것이 아니므로 환불 대상이 아니고, 차감이
     * 무상부터 나가므로 <b>사용자가 산 재화보다 먼저 쓰인다</b> — 환불 요구가 오면 산 만큼은
     * 그대로 남아 있다.
     *
     * @return 지급했으면 {@code true}. 이미 지급된 인증이면 {@code false} 이고 아무것도 하지 않는다.
     */
    @Transactional
    public boolean grantForVerification(Member member, Long verificationId, int amount) {
        if (amount <= 0) {
            // reward 가 0 인 챌린지가 있다. 지급할 것이 없으면 행도 거래도 만들지 않는다 —
            // 0 짜리 기록이 쌓이면 나중에 "지급받았는가" 를 세는 쪽이 헷갈린다.
            return false;
        }
        Optional<RewardGrant> already = rewardGrantRepository
                .findByMemberIdAndReasonAndReferenceId(
                        member.getId(), RewardReason.VERIFICATION, verificationId);
        if (already.isPresent()) {
            return false;
        }

        // 유니크 제약이 최종 방어선이다. 위 조회는 흔한 경우를 예외 없이 넘기기 위한 것이고,
        // 따닥으로 동시에 들어오면 둘 다 조회를 통과하므로 제약이 하나를 떨군다.
        RewardGrant grant = rewardGrantRepository.saveAndFlush(RewardGrant.builder()
                .member(member)
                .reason(RewardReason.VERIFICATION)
                .referenceId(verificationId)
                .currency(REWARD_CURRENCY)
                .amount(amount)
                .build());

        walletCommandService.grant(new WalletCommand(
                member.getId(), REWARD_CURRENCY, WalletTransactionType.CHALLENGE_REWARD,
                grant.idempotencyKey(), RewardReason.VERIFICATION.name(), verificationId),
                0, amount);
        return true;
    }

    /**
     * 인증을 지울 때 준 것을 되돌린다. <b>모자라면 예외를 던져 삭제까지 막는다.</b>
     *
     * <p>부채를 남기지 않기 위해서다. 미상환을 허용하면 그만큼 제재를 걸어야 하는데, 제재
     * 중에는 인증을 못 해 갚을 수단이 없다 — 빠져나올 수 없는 상태가 된다.
     *
     * <p>지급 행을 <b>지운다</b>. 남겨 두면 유니크 제약 때문에 다시 인증해도 못 받는다.
     * 무슨 일이 있었는지는 원장에 지급·회수 두 줄로 남는다.
     */
    @Transactional
    public void clawbackForVerification(Long memberId, Long verificationId) {
        Optional<RewardGrant> found = rewardGrantRepository
                .findByMemberIdAndReasonAndReferenceId(
                        memberId, RewardReason.VERIFICATION, verificationId);
        if (found.isEmpty()) {
            // 지급이 없었으면 되돌릴 것도 없다. 리워드가 0 인 챌린지이거나, 이 기능이 생기기
            // 전에 올린 인증이거나, 보류라 아직 안 준 경우다.
            return;
        }
        RewardGrant grant = found.get();

        // 차감을 먼저 시도하고 예외를 잡아 바꿔치는 방법도 있지만, 그러면 "얼마가 모자란지"
        // 를 알 수 없다. 사용자에게 몇 개를 더 모으면 되는지 알려주려면 지금 잔액이 필요하다.
        int balance = walletQueryService.getBalances(memberId).balances().stream()
                .filter(b -> b.currency() == grant.getCurrency())
                .mapToInt(WalletResDTO.Balance::balance)
                .findFirst()
                .orElse(0);
        if (balance < grant.getAmount()) {
            throw new RewardClawbackException(grant.getAmount(), balance);
        }

        walletCommandService.deduct(new WalletCommand(
                memberId, grant.getCurrency(), WalletTransactionType.CHALLENGE_REWARD_CLAWBACK,
                grant.clawbackIdempotencyKey(), RewardReason.VERIFICATION.name(), verificationId),
                grant.getAmount());
        rewardGrantRepository.delete(grant);
    }
}
