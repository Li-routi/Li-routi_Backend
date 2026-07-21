package com.lirouti.domain.challenge.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.lirouti.domain.challenge.entity.MemberChallenge;

import jakarta.persistence.LockModeType;

public interface MemberChallengeRepository
        extends JpaRepository<MemberChallenge, Long>, MemberChallengeRepositoryCustom {

    // 참여/이탈용. UNIQUE(member_id, challenge_id)이므로 회원·챌린지당 한 행이다(이탈해도 행은 남는다).
    Optional<MemberChallenge> findByMemberIdAndChallengeId(Long memberId, Long challengeId);

    /**
     * 참여(재참여) 처리용 행 락 조회.
     * 이미 그만둔(active=false) 행에 동시 재참여 요청이 오면 둘 다 통과해 버리므로,
     * PESSIMISTIC_WRITE로 행을 잠가 직렬화한다. 락을 얻은 쪽은 최신 커밋본을 읽으므로
     * 앞선 요청이 살려둔 active=true를 보고 ALREADY_PARTICIPATING(409)로 걸러진다.
     * member.id/challenge.id는 member_challenge의 FK 컬럼이라 조인 없이 이 행만 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mc
            from MemberChallenge mc
            where mc.member.id = :memberId
              and mc.challenge.id = :challengeId
            """)
    Optional<MemberChallenge> findByMemberIdAndChallengeIdForUpdate(Long memberId, Long challengeId);
}
