package com.lirouti.domain.challenge.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lirouti.domain.challenge.entity.ChallengeVerification;

public interface ChallengeVerificationRepository
        extends JpaRepository<ChallengeVerification, Long>, ChallengeVerificationRepositoryCustom {

    /**
     * 현재 회차의 오늘 인증 행. 있으면 당일 재인증(덮어쓰기) 대상이다.
     * UNIQUE(member_challenge_id, participation_round, verified_date) 인덱스를 그대로 탄다.
     *
     * 이 조회로 "이미 인증했는지"를 판단하지만, 조회와 저장 사이의 동시 요청은 막지 못한다.
     * 그 경합은 위 유니크 제약이 DB에서 막는다.
     */
    Optional<ChallengeVerification> findByMemberChallengeIdAndParticipationRoundAndVerifiedDate(
            Long memberChallengeId,
            Integer participationRound,
            LocalDate verifiedDate
    );
}
