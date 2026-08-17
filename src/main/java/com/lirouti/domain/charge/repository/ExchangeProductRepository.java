package com.lirouti.domain.charge.repository;

import com.lirouti.domain.charge.entity.ExchangeProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeProductRepository extends JpaRepository<ExchangeProduct, Long> {

    List<ExchangeProduct> findAllByActiveTrueOrderBySortOrderAscIdAsc();
}
