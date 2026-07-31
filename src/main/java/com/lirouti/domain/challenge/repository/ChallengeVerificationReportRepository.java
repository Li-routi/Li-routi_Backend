package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.ChallengeVerificationReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeVerificationReportRepository
        extends JpaRepository<ChallengeVerificationReport, Long> {

    /**
     * 그 인증에 쌓인 신고 수. 임계값 도달 여부를 신고 저장 직후에 판단한다.
     *
     * 조회가 아니라 신고 때 세는 이유는 읽기 경로를 무겁게 하지 않기 위해서다.
     * 신고는 드물고 피드 조회는 잦다.
     */
    long countByChallengeVerificationId(Long challengeVerificationId);
}
