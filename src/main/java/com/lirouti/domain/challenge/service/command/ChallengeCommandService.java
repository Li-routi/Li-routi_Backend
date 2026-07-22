package com.lirouti.domain.challenge.service.command;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.util.TimeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeCommandService {
    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final MemberRepository memberRepository;
    // 미디어 key의 발급 규칙·공개 URL 조립은 media 도메인이 소유한다. DB를 다루지 않는 유틸성 서비스다.
    private final MediaService mediaService;

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
        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeId(memberId, challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING));

        if (!memberChallenge.isParticipating()) {
            throw new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING);
        }
        memberChallenge.leave();

        return ChallengeConverter.toParticipation(memberChallenge);
    }

    /**
     * 챌린지 인증. 사진과 코멘트를 등록하고 같은 트랜잭션에서 스트릭을 갱신한다.
     *
     * 오늘 이미 인증했으면 행을 새로 만들지 않고 덮어쓴다(당일 재인증). 이때 스트릭은 오르지 않는다.
     * 인증 INSERT와 스트릭 갱신을 한 트랜잭션에 두는 것이 중복 증가를 막는 핵심이다 —
     * 동시 요청은 UNIQUE(member_challenge_id, participation_round, verified_date)에 걸려 실패하고,
     * 그 예외로 트랜잭션 전체가 롤백되어 스트릭 갱신도 함께 되돌아간다.
     *
     * 비활성 챌린지라도 참여 중이면 인증할 수 있다. 운영이 챌린지를 내려도 진행 중인 스트릭이
     * 끊기지 않게 하기 위해서이며, 이탈(leave)이 챌린지 active를 보지 않는 것과 같은 기준이다.
     */
    @Transactional
    public ChallengeResDTO.Verification verify(
            Long memberId,
            Long challengeId,
            ChallengeReqDTO.Verify request
    ) {
        // key는 서버가 발급하지만 요청으로 되돌아오므로, 저장 전에 발급 규칙과 대조한다.
        mediaService.validateMediaKey(request.mediaKey(), MediaPurpose.CHALLENGE_VERIFICATION);

        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeId(memberId, challengeId)
                .filter(MemberChallenge::isParticipating)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING));

        // 기준일과 인증 시각을 같은 순간에서 뽑는다. now()를 두 번 부르면 자정 경계에서
        // 날짜와 시각이 서로 다른 날을 가리킬 수 있다.
        ZonedDateTime now = ZonedDateTime.now(TimeUtil.KST);
        LocalDate today = now.toLocalDate();
        LocalDateTime verifiedAt = now.toLocalDateTime();

        Optional<ChallengeVerification> todayVerification = challengeVerificationRepository
                .findByMemberChallengeIdAndParticipationRoundAndVerifiedDate(
                        memberChallenge.getId(),
                        memberChallenge.getParticipationRound(),
                        today
                );
        boolean reverified = todayVerification.isPresent();

        ChallengeVerification verification = todayVerification
                .map(existing -> {
                    existing.reverify(request.mediaKey(), request.content(), verifiedAt);
                    return existing;
                })
                .orElseGet(() -> createVerification(memberChallenge, request, today, verifiedAt));

        // 재인증이면 applyVerification이 오늘 날짜를 보고 스트릭을 그대로 둔다.
        memberChallenge.applyVerification(today);

        return ChallengeConverter.toVerification(
                verification,
                mediaService.resolvePublicUrl(verification.getImageUrl()),
                memberChallenge.currentStreakAsOf(today),
                reverified
        );
    }

    private ChallengeVerification createVerification(
            MemberChallenge memberChallenge,
            ChallengeReqDTO.Verify request,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt
    ) {
        ChallengeVerification verification = ChallengeVerification.builder()
                .memberChallenge(memberChallenge)
                .participationRound(memberChallenge.getParticipationRound())
                .verifiedDate(verifiedDate)
                .verifiedAt(verifiedAt)
                .imageUrl(request.mediaKey())
                .content(request.content())
                .build();
        try {
            // "오늘 인증이 없다"는 선검사와 저장 사이의 동시 요청 경합은 유니크 제약이 막는다.
            // saveAndFlush로 그 실패를 여기서 잡아 409로 바꾼다.
            //
            // 두 예외를 모두 잡는다. 같은 유니크 키로 INSERT가 겹칠 때 InnoDB는 늘 중복 키 오류
            // (DataIntegrityViolationException)를 주지 않는다. 중복을 만난 쪽이 기존 인덱스 레코드에
            // 락을 요청하면서 데드락으로 판정되면 CannotAcquireLockException으로 올라온다
            // (실제로 인증 동시성 테스트에서 이 경로가 관측됐다). 둘 다 "같은 날 인증 경합에서 졌다"는
            // 같은 의미이고, 어느 쪽이든 이 트랜잭션은 롤백되므로 처리도 같다.
            //
            // 예외를 잡되 삼키지는 않는다. 여기서 던지는 ChallengeException이 트랜잭션을 롤백시켜
            // 위의 스트릭 갱신까지 함께 되돌린다. 만약 "이미 인증했으니 무시하고 진행"으로 처리하면
            // 스트릭 갱신만 커밋되어 값이 두 번 오른다(database-schema.md).
            //
            // 재시도하지 않는 이유: 재시도는 먼저 들어온 요청의 인증을 덮어쓰게 된다.
            // 같은 사용자의 중복 클릭이므로 409로 알리는 편이 정직하다.
            return challengeVerificationRepository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            throw new ChallengeException(ChallengeErrorCode.VERIFICATION_CONFLICT);
        }
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
