package com.lirouti.domain.challenge.service.command;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeCommandService {
    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final MemberRepository memberRepository;
    // 인증 저장의 트랜잭션 경계는 이 빈에 있다. 자기 호출로는 트랜잭션이 걸리지 않아 분리했다.
    private final ChallengeVerificationCommandService challengeVerificationCommandService;
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
        // 참여·인증과 같은 행을 바꾸므로 같은 방식으로 잠근다. 락 없이 읽으면 읽은 뒤 커밋된
        // 다른 명령의 결과를 이 트랜잭션의 오래된 스냅샷이 덮어쓴다(#53).
        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeIdForUpdate(memberId, challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING));

        if (!memberChallenge.isParticipating()) {
            throw new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING);
        }
        memberChallenge.leave();

        return ChallengeConverter.toParticipation(memberChallenge);
    }

    /**
     * 챌린지 인증. <b>트랜잭션 밖에서 끝내야 하는 검증을 먼저 하고</b> 저장은 다른 빈에 위임한다.
     *
     * 이 메서드에 @Transactional이 없는 것은 의도다. 아래 미디어 바이트 검증(#22)이 S3를 실제로
     * 호출하는데, 트랜잭션 안에서 부르면 DB 커넥션과 참여 행 락을 S3 왕복 시간만큼 붙잡는다
     * (service_convention: 트랜잭션 내 장시간 외부 API 호출 금지).
     * 저장·스트릭 갱신의 트랜잭션 경계는 {@link ChallengeVerificationCommandService#save}에 있다.
     * 자기 호출로는 트랜잭션이 걸리지 않아 빈을 나눴다(AuthService → MemberCommandService와 같은 모양).
     *
     * 현재는 자가인증이다 — 형식과 바이트가 맞으면 그대로 통과한다. 사진이 챌린지 의도(이름·설명)에
     * 맞는지 심사하는 로직은 아직 없으며 #40에서 아래 TODO 자리에 들어간다.
     */
    public ChallengeResDTO.Verification verify(
            Long memberId,
            Long challengeId,
            ChallengeReqDTO.Verify request
    ) {
        // ① key는 서버가 발급하지만 요청으로 되돌아오므로, 저장 전에 발급 규칙과 대조한다.
        mediaService.validateMediaKey(request.mediaKey(), MediaPurpose.CHALLENGE_VERIFICATION);

        // ② 업로드된 실제 바이트가 그 형식이 맞는지 확인한다(#22). S3를 호출하므로 트랜잭션 밖이다.
        //    presigned URL은 요청 메타데이터(Content-Type·Length)만 강제할 뿐 바이트 내용은 막지 못한다.
        //    이 호출이 오브젝트 존재 확인도 겸한다 — 업로드하지 않은 key면 404로 걸린다.
        mediaService.validateUploadedBytes(request.mediaKey(), MediaPurpose.CHALLENGE_VERIFICATION);

        // TODO(#40): 사진이 챌린지 의도(challenge.name + description)에 맞는지 AI 심사.
        //  자리는 여기다 — ②와 같은 "트랜잭션 밖" 구간이라 그대로 추가하면 된다.
        //  방식·제공자·임계값·반려 UX·판정결과 저장(스키마 영향)은 #40에서 확정한다.

        // ③ 저장·스트릭 갱신. 여기서부터가 트랜잭션이다.
        return challengeVerificationCommandService.save(memberId, challengeId, request);
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
