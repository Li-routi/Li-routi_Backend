package com.lirouti.domain.challenge.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lirouti.domain.challenge.entity.ChallengeVerificationReport;

public interface ChallengeVerificationReportRepository
        extends JpaRepository<ChallengeVerificationReport, Long> {
}
