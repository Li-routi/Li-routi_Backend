package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.MemberChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MemberChallengeRepository
        extends JpaRepository<MemberChallenge, Long>, MemberChallengeRepositoryCustom {

    // 참여/이탈용. UNIQUE(member_id, challenge_id)이므로 회원·챌린지당 한 행이다(이탈해도 행은 남는다).
    Optional<MemberChallenge> findByMemberIdAndChallengeId(Long memberId, Long challengeId);

    /**
     * 참여 행을 바꾸는 명령(참여·재참여·이탈·인증) 공용 행 락 조회.
     *
     * 이미 그만둔(active=false) 행에 동시 재참여 요청이 오면 둘 다 통과해 버리므로,
     * PESSIMISTIC_WRITE로 행을 잠가 직렬화한다. 락을 얻은 쪽은 최신 커밋본을 읽으므로
     * 앞선 요청이 살려둔 active=true를 보고 ALREADY_PARTICIPATING(409)로 걸러진다.
     * member.id/challenge.id는 member_challenge의 FK 컬럼이라 조인 없이 이 행만 잠근다.
     *
     * 재참여만 잠그면 부족하다(#53). 인증·이탈이 락 없이 읽으면 읽은 뒤 커밋된 다른 명령의
     * 결과를 오래된 스냅샷으로 덮어쓴다. 엔티티에 @DynamicUpdate가 없어 flush가 전체 컬럼을
     * 실으므로, 자기가 건드리지도 않은 active·participation_round까지 되돌아간다.
     * 그래서 이 행을 변경하는 명령은 예외 없이 이 메서드로 읽는다.
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
