package com.lirouti.domain.challenge.service.command;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 챌린지 참여 상태를 바꾼다 — 참여와 이탈.
 *
 * <p><b>인증은 여기 없다.</b> 인증·신고·좋아요·메모 수정·삭제는 {@code verification} 도메인이
 * 소유한다. 예전에는 저장만 그쪽에 있고 오케스트레이션이 여기 남아 있어, 인증 기능을 붙일 때
 * 어디에 두어야 하는지가 매번 애매했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeCommandService {
    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final MemberRepository memberRepository;

    /**
     * 챌린지 참여. 처음이면 새 행을, 예전에 그만뒀던 챌린지면 기존 행을 되살린다(재참여, 회차+1).
     * 이미 참여 중이면 예외. 없거나 비활성 챌린지면 404.
     */
    @Transactional
    public ChallengeResDTO.Participation participate(Long memberId, Long challengeId) {
        Challenge challenge = challengeRepository.findByIdAndActiveTrue(challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.CHALLENGE_NOT_FOUND));

        // 재참여 경합 방지를 위해 기존 행을 락 걸고 조회한다(행이 없으면 잠글 것도 없음 → 신규 참여로).
        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeIdForUpdate(memberId, challengeId)
                .map(this::rejoinOrReject)
                .orElseGet(() -> createParticipation(memberId, challenge));

        return ChallengeConverter.toParticipation(memberChallenge);
    }

    /**
     * 챌린지 이탈. active를 끄고 참여 이력·인증은 보존한다. 참여 중이 아니면 예외.
     * 비활성 챌린지라도 참여 중이면 이탈할 수 있어 챌린지 active는 확인하지 않는다.
     */
    @Transactional
    public ChallengeResDTO.Participation leave(Long memberId, Long challengeId) {
        // 참여·인증과 같은 행을 바꾸므로 같은 방식으로 잠근다. 락 없이 읽으면 읽은 뒤 커밋된
        // 다른 명령의 결과를 이 트랜잭션의 오래된 스냅샷이 덮어쓴다.
        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeIdForUpdate(memberId, challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING));

        if (!memberChallenge.isParticipating()) {
            throw new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING);
        }
        memberChallenge.leave();

        return ChallengeConverter.toParticipation(memberChallenge);
    }

    private MemberChallenge rejoinOrReject(MemberChallenge existing) {
        if (existing.isParticipating()) {
            throw new ChallengeException(ChallengeErrorCode.ALREADY_PARTICIPATING);
        }
        existing.rejoin(LocalDateTime.now());
        return existing;
    }

    private MemberChallenge createParticipation(Long memberId, Challenge challenge) {
        // 인증 없이 회원 FK만 연결하면 되므로 프록시 참조로 불필요한 회원 조회를 피한다.
        Member member = memberRepository.getReferenceById(memberId);
        MemberChallenge memberChallenge = MemberChallenge.builder()
                .member(member)
                .challenge(challenge)
                .participationRound(1)
                .currentStreak(0)
                .joinedAt(LocalDateTime.now())
                .active(true)
                .build();
        try {
            // 참여 행이 없다는 선검사와 저장 사이의 동시 요청 경합은 UNIQUE(member,challenge)가 막는다.
            // 이때 saveAndFlush로 제약 위반을 여기서 잡아 409로 변환한다.
            //
            // 여기서 도달 가능한 무결성 위반은 이 유니크 제약뿐이다. 회원은 소프트 삭제만 하고(하드 삭제 없음)
            // 인증된 회원의 member 행은 항상 존재하므로 member_id FK 위반은 발생하지 않는다.
            // (만약 회원 하드 삭제를 도입하면 이 catch를 유니크 제약으로 좁혀야 한다.)
            return memberChallengeRepository.saveAndFlush(memberChallenge);
        } catch (DataIntegrityViolationException e) {
            throw new ChallengeException(ChallengeErrorCode.ALREADY_PARTICIPATING);
        }
    }
}
