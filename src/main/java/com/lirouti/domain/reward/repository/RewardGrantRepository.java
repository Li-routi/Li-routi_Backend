package com.lirouti.domain.reward.repository;

import com.lirouti.domain.reward.entity.RewardGrant;
import com.lirouti.domain.reward.enums.RewardReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RewardGrantRepository extends JpaRepository<RewardGrant, Long> {

    Optional<RewardGrant> findByMemberIdAndReasonAndReferenceId(
            Long memberId, RewardReason reason, Long referenceId);
}
