package com.lirouti.domain.charge.entity;

import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재화로 사는 재화 묶음.
 *
 * <p><b>비율을 코드에 박지 않는다.</b> 묶음마다 다르고(많이 살수록 유리하다) 운영이 바꾼다.
 * 시드로 관리한다.
 */
@Entity
@Getter
@Table(name = "exchange_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_currency", nullable = false, length = 20)
    private Currency fromCurrency;

    @Column(name = "from_amount", nullable = false)
    private int fromAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_currency", nullable = false, length = 20)
    private Currency toCurrency;

    @Column(name = "to_amount", nullable = false)
    private int toAmount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    private ExchangeProduct(Currency fromCurrency, int fromAmount,
                            Currency toCurrency, int toAmount, int sortOrder, boolean active) {
        if (fromCurrency == toCurrency) {
            throw new IllegalArgumentException("같은 재화끼리는 교환할 수 없습니다.");
        }
        if (fromAmount <= 0 || toAmount <= 0) {
            throw new IllegalArgumentException("교환 수량은 1 이상이어야 합니다.");
        }
        this.fromCurrency = fromCurrency;
        this.fromAmount = fromAmount;
        this.toCurrency = toCurrency;
        this.toAmount = toAmount;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
