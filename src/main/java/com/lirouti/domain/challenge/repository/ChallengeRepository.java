package com.lirouti.domain.challenge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lirouti.domain.challenge.entity.Challenge;

public interface ChallengeRepository extends JpaRepository<Challenge, Long>, ChallengeRepositoryCustom {

    Optional<Challenge> findByIdAndActiveTrue(Long id);
}
