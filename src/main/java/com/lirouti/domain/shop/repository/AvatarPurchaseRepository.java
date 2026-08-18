package com.lirouti.domain.shop.repository;

import com.lirouti.domain.shop.entity.AvatarPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AvatarPurchaseRepository extends JpaRepository<AvatarPurchase, Long> {

    /**
     * 같은 회원이 같은 키로 이미 산 적이 있는가.
     *
     * <p>선점이 유니크 제약 위에서 이뤄지므로, 이 조회는 <b>제약에 튕긴 뒤</b> 무엇에 튕겼는지
     * 알아보려고 쓴다. 먼저 조회하고 없으면 넣는 방식이 아니다 — 그렇게 하면 두 요청이 모두
     * 빈 결과를 받아 각자 INSERT 한다.
     */
    Optional<AvatarPurchase> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);
}
