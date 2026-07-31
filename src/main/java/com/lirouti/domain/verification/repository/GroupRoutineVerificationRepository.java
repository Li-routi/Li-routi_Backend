package com.lirouti.domain.verification.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lirouti.domain.verification.entity.GroupRoutineVerification;

public interface GroupRoutineVerificationRepository
        extends JpaRepository<GroupRoutineVerification, Long> {

    /** 그 할당에 이미 인증이 있는지. 할당 1건에 인증 1건이므로 단건이다. */
    Optional<GroupRoutineVerification> findByAssignmentId(Long assignmentId);
}
