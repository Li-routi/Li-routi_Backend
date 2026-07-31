package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.ChallengeVerificationReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeVerificationReportRepository
        extends JpaRepository<ChallengeVerificationReport, Long> {
}
