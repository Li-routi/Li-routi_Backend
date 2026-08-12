package com.lirouti.domain.shop.repository;

import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.AvatarSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberAvatarEquipmentRepository extends JpaRepository<MemberAvatarEquipment, Long> {

    List<MemberAvatarEquipment> findAllByMemberId(Long memberId);

    /** 여러 회원의 현재 착용 아이템을 회원·슬롯 순서로 한 번에 읽는다. */
    @Query("""
            select equipment
            from MemberAvatarEquipment equipment
            join fetch equipment.member member
            join fetch equipment.avatarItem avatarItem
            where member.id in :memberIds
            order by member.id asc, equipment.slot asc
            """)
    List<MemberAvatarEquipment> findAllByMemberIdInWithMemberAndAvatarItem(
            @Param("memberIds") List<Long> memberIds
    );

    Optional<MemberAvatarEquipment> findByMemberIdAndSlot(Long memberId, AvatarSlot slot);

    void deleteAllByMemberId(Long memberId);
}
