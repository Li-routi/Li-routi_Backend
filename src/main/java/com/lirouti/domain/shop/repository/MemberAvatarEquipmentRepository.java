package com.lirouti.domain.shop.repository;

import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.AvatarSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberAvatarEquipmentRepository extends JpaRepository<MemberAvatarEquipment, Long> {

    List<MemberAvatarEquipment> findAllByMemberId(Long memberId);

    Optional<MemberAvatarEquipment> findByMemberIdAndSlot(Long memberId, AvatarSlot slot);

    void deleteAllByMemberId(Long memberId);
}
