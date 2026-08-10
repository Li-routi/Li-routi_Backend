package com.lirouti.domain.wallet.service.query;

import com.lirouti.domain.wallet.converter.WalletConverter;
import com.lirouti.domain.wallet.dto.response.WalletResDTO;
import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.repository.MemberWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final MemberWalletRepository memberWalletRepository;

    /**
     * 상점 헤더의 잔액.
     *
     * <p>회원 존재 여부를 따로 확인하지 않는다. 이 경로는 인증을 통과한 요청만 닿고, 없는
     * 회원이면 지갑도 없어 0 이 나간다. 회원 조회를 한 번 더 하는 것은 모든 화면에서 도는
     * 요청에 쿼리를 하나 더 얹는 일이라 값이 맞지 않는다.
     */
    @Transactional(readOnly = true)
    public WalletResDTO.Balances getBalances(Long memberId) {
        Map<Currency, MemberWallet> wallets = memberWalletRepository.findAllByMemberId(memberId)
                .stream()
                .collect(Collectors.toMap(MemberWallet::getCurrency, Function.identity()));
        return WalletConverter.toBalances(wallets);
    }
}
