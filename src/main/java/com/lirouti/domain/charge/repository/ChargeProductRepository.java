package com.lirouti.domain.charge.repository;

import com.lirouti.domain.charge.entity.ChargeProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeProductRepository extends JpaRepository<ChargeProduct, Long> {

    List<ChargeProduct> findAllByActiveTrueOrderBySortOrderAscIdAsc();
}
