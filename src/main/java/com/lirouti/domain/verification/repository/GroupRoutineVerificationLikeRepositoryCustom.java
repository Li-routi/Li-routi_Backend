package com.lirouti.domain.verification.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupRoutineVerificationLikeRepositoryCustom {
    Map<Long, Long> countByVerificationIds(List<Long> verificationIds);

    Set<Long> findLikedVerificationIds(List<Long> verificationIds, Long memberId);
}
