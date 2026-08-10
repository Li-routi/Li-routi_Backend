package com.lirouti.domain.shop.repository;

import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.enums.AvatarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AvatarItemRepository extends JpaRepository<AvatarItem, Long> {

    /**
     * 상점 목록. <b>파는 것과 가진 것을 함께 내린다.</b>
     *
     * <p>판매 중단된 것도 <b>보유했으면 포함한다.</b> 빼면 사용자가 가진 것을 화면에서 고를 수
     * 없고, 착용 저장이 착장 전체를 받으므로 목록에 없다는 이유로 조용히 벗겨진다.
     *
     * @param slot     {@code null} 이면 전체. 화면의 "전체" 탭이 이것이다
     * @param ownedIds 이 회원이 보유한 아이템 id. 비어 있으면 판매 중인 것만 나온다
     */
    @Query("""
            select i from AvatarItem i
            where (:slot is null or i.slot = :slot)
              and (i.active = true or i.id in :ownedIds)
            order by i.slot asc, i.sortOrder asc, i.id asc
            """)
    List<AvatarItem> findForShop(@Param("slot") AvatarSlot slot,
                                 @Param("ownedIds") Collection<Long> ownedIds);
}
