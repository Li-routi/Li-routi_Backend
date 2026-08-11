package com.lirouti.domain.charge.service.query;

import com.lirouti.domain.charge.converter.ChargeConverter;
import com.lirouti.domain.charge.dto.response.ChargeResDTO;
import com.lirouti.domain.charge.repository.ChargeProductRepository;
import com.lirouti.domain.charge.repository.ExchangeProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChargeQueryService {

    private final ChargeProductRepository chargeProductRepository;
    private final ExchangeProductRepository exchangeProductRepository;

    /** 파란보석 탭 — 현금으로 사는 묶음. */
    @Transactional(readOnly = true)
    public ChargeResDTO.ChargeItems getChargeProducts() {
        return ChargeConverter.toChargeItems(
                chargeProductRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc());
    }

    /** 주황보석 탭 — 재화로 사는 묶음. */
    @Transactional(readOnly = true)
    public ChargeResDTO.ExchangeItems getExchangeProducts() {
        return ChargeConverter.toExchangeItems(
                exchangeProductRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc());
    }
}
