package com.lirouti.domain.shop.repository;

import com.lirouti.domain.shop.entity.MemberAvatarItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MemberAvatarItemRepository extends JpaRepository<MemberAvatarItem, Long> {

    boolean existsByMemberIdAndAvatarItemId(Long memberId, Long avatarItemId);

    @Query("select mi.avatarItem.id from MemberAvatarItem mi where mi.member.id = :memberId")
    List<Long> findOwnedItemIds(@Param("memberId") Long memberId);

    /** 착용 저장에서 "전부 보유했는가" 를 한 번에 확인한다. */
    @Query("""
            select mi.avatarItem.id from MemberAvatarItem mi
            where mi.member.id = :memberId and mi.avatarItem.id in :itemIds
            """)
    List<Long> findOwnedItemIdsIn(@Param("memberId") Long memberId,
                                  @Param("itemIds") Collection<Long> itemIds);
}
