package com.lirouti.domain.shop.repository;

import com.lirouti.domain.shop.entity.MemberAvatarItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MemberAvatarItemRepository extends JpaRepository<MemberAvatarItem, Long> {

    boolean existsByMemberIdAndAvatarItemId(Long memberId, Long avatarItemId);

    /**
     * 한 구매에 담겼던 아이템. <b>id 오름차순이 곧 요청 순서다</b> — 저장한 순서가 그대로 남는다.
     *
     * <p>같은 멱등 키로 다시 들어온 요청에 앞서 성사된 구매를 돌려줄 때 쓴다. 응답의 아이템
     * 순서가 처음 응답과 같아야 클라이언트가 대조할 수 있다.
     */
    List<MemberAvatarItem> findAllByAvatarPurchaseIdOrderByIdAsc(Long avatarPurchaseId);

    @Query("select mi.avatarItem.id from MemberAvatarItem mi where mi.member.id = :memberId")
    List<Long> findOwnedItemIds(@Param("memberId") Long memberId);

    /** 착용 저장에서 "전부 보유했는가" 를 한 번에 확인한다. */
    @Query("""
            select mi.avatarItem.id from MemberAvatarItem mi
            where mi.member.id = :memberId and mi.avatarItem.id in :itemIds
            """)
    List<Long> findOwnedItemIdsIn(@Param("memberId") Long memberId,
                                  @Param("itemIds") Collection<Long> itemIds);

    /**
     * 업적 보상 등, "이미 있으면 그냥 넘어가면 되는" 지급 경로 전용 삽입.
     *
     * <p>exists 확인 후 save 하는 방식은 동시 요청 두 개가 나란히 확인을 통과하면 하나가
     * 유니크 제약({@code uk_member_avatar_item_member_item})에 걸려 실패한다. 여기서는
     * INSERT IGNORE 로 그 경합 자체를 DB 단에서 무해하게 흡수한다 — 이미 있으면 0행,
     * 처음이면 1행. 호출 측은 반환값을 몰라도 되는 멱등 지급이라 결과를 무시해도 된다.
     *
     * <p>native insert 라 영속성 컨텍스트가 이 변경을 모른다. 같은 트랜잭션에서 방금 지급한
     * 행을 JPA로 다시 읽어야 한다면 {@code clearAutomatically = true} 로 인한 초기화를
     * 감안하거나 호출 순서를 조정해야 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO member_avatar_item
                (member_id, avatar_item_id, currency, paid_price, purchased_at, grant_reason, created_at, updated_at)
            VALUES (:memberId, :avatarItemId, :currency, 0, NOW(6), :grantReason, NOW(6), NOW(6))
            """, nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId,
                       @Param("avatarItemId") Long avatarItemId,
                       @Param("currency") String currency,
                       @Param("grantReason") String grantReason);
}
