package com.lirouti.domain.challenge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lirouti.domain.challenge.entity.MemberChallenge;

public interface MemberChallengeRepository
        extends JpaRepository<MemberChallenge, Long>, MemberChallengeRepositoryCustom {

    // 참여/이탈용. UNIQUE(member_id, challenge_id)이므로 회원·챌린지당 한 행이다(이탈해도 행은 남는다).
    Optional<MemberChallenge> findByMemberIdAndChallengeId(Long memberId, Long challengeId);
}
