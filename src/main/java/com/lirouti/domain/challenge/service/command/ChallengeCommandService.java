package com.lirouti.domain.challenge.service.command;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.request.ChallengeReqDTO;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.ChallengeVerificationReport;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationLikeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationReportRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.properties.ChallengeReportProperties;
import com.lirouti.global.util.TimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeCommandService {
    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    // 신고가 대상 인증을 찾을 때 쓴다. 인증 저장은 ChallengeVerificationCommandService가 맡는다.
    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final ChallengeVerificationReportRepository challengeVerificationReportRepository;
    private final ChallengeVerificationLikeRepository challengeVerificationLikeRepository;
    private final MemberRepository memberRepository;
    private final ChallengeReportProperties challengeReportProperties;
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

    /**
     * 인증 신고. 신고자 본인의 피드에서만 그 인증이 가려진다 — 인증은 삭제되지 않고
     * 다른 회원에게는 그대로 보인다(database-schema.md).
     *
     * 자기 인증을 신고하는 것을 막지 않는다. 기획에 그런 제약이 없고, 막지 않아도 결과는
     * "본인 피드에서 본인 사진이 안 보인다"뿐이라 해가 없다. 필요해지면 조건을 추가한다.
     *
     * 중복 신고는 UNIQUE(challenge_verification_id, reporter_id)가 막는다. "이미 신고했는지"를
     * 먼저 조회해 판단하지 않는 이유는 조회와 저장 사이의 동시 요청을 막지 못하기 때문이다.
     * 제약 위반을 잡아 409로 바꾼다(인증 저장과 같은 방식).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChallengeResDTO.Report report(
            Long memberId,
            Long challengeId,
            Long verificationId,
            ChallengeReqDTO.Report request
    ) {
        // 경로의 challengeId와 실제 인증의 챌린지가 맞는지까지 확인한다. 어긋나면 404다.
        ChallengeVerification verification = challengeVerificationRepository
                .findByIdAndMemberChallengeChallengeId(verificationId, challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.VERIFICATION_NOT_FOUND));

        // 숨김 판정을 직렬화하기 위해 인증 행을 잠근다. 신고 INSERT 전에 잡아야 한다 —
        // 저장 후에 잠그면 그 사이 다른 트랜잭션이 이미 세기를 마치고 지나갈 수 있다.
        verification = challengeVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.VERIFICATION_NOT_FOUND));

        // 신고자는 FK만 필요하므로 프록시 참조로 불필요한 회원 조회를 피한다(참여 생성과 같은 이유).
        Member reporter = memberRepository.getReferenceById(memberId);

        ChallengeVerificationReport report = ChallengeVerificationReport.builder()
                .challengeVerification(verification)
                .reporter(reporter)
                .reason(request.reason())
                .build();
        ChallengeVerificationReport saved;
        try {
            // saveAndFlush로 제약 위반을 이 자리에서 잡는다. 커밋 시점까지 미루면
            // 트랜잭션 밖에서 터져 도메인 코드로 바꿀 수 없다.
            //
            // 두 예외를 모두 잡는 이유는 인증 저장과 같다. 같은 유니크 키로 INSERT가 겹칠 때
            // InnoDB가 중복 키 오류 대신 데드락으로 판정해 CannotAcquireLockException을 줄 수 있다.
            saved = challengeVerificationReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            throw new ChallengeException(ChallengeErrorCode.ALREADY_REPORTED);
        }

        hideIfReportedEnough(verification);
        return ChallengeConverter.toReport(saved);
    }

    /**
     * 신고가 임계값만큼 쌓였으면 전체 회원에게 가린다.
     *
     * <p><b>세는 시점을 조회가 아니라 신고 때로 둔다.</b> 피드에서 매번
     * {@code having count(*) >= N}으로 세면 읽기 경로가 무거워진다. 신고는 드물고 조회는
     * 잦으므로 쓰기 시점 계산이 맞다.
     *
     * <p><b>정확히 세려면 두 가지가 다 필요하다.</b>
     *
     * <p>하나는 <b>인증 행 잠금</b>이다. 잠금이 없으면 동시 신고가 각자 INSERT 하고 각자 세는데,
     * 서로의 미커밋 INSERT 가 안 보여 전부 임계값 미만으로 판단한다. 정확히 임계값만큼만 동시에
     * 들어오면 그 뒤로 신고가 없는 한 <b>영원히 가려지지 않는다.</b>
     *
     * <p>다른 하나는 <b>READ_COMMITTED</b>다. MySQL 기본값인 REPEATABLE READ 에서는 트랜잭션의
     * 첫 조회 시점에 스냅샷이 고정되어, 잠금을 잡고 기다린 뒤에 세어도 그동안 커밋된 신고가
     * 보이지 않는다. 잠금만으로는 순서만 정해질 뿐 값이 낡은 채다. 실측으로 확인했다 —
     * 잠금만 넣었을 때 동시성 테스트가 그대로 실패했다.
     *
     * <p>신고는 드문 요청이라 이 잠금이 경합을 만들 일은 거의 없다.
     *
     * <p>가려도 인증 행과 스트릭은 그대로다. 막는 것은 노출뿐이다.
     */
    private void hideIfReportedEnough(ChallengeVerification verification) {
        if (verification.isHidden()) {
            return;
        }
        long reportCount = challengeVerificationReportRepository
                .countByChallengeVerificationId(verification.getId());
        if (reportCount < challengeReportProperties.getHideThreshold()) {
            return;
        }
        verification.hide(LocalDateTime.now(TimeUtil.KST));
        log.warn("신고 누적으로 인증을 전체 숨김 처리했습니다. verificationId={}, 신고={}건, 임계값={}",
                verification.getId(), reportCount, challengeReportProperties.getHideThreshold());
    }

    /**
     * 인증 게시물에 좋아요(#63). 이미 눌러둔 상태여도 성공으로 처리한다.
     *
     * 좋아요는 토글이라 같은 요청이 두 번 오는 것이 정상 사용이다(따닥 누르기). 신고처럼 409를
     * 돌려주면 화면이 흔들리므로, 최종 상태를 그대로 응답한다(database-schema.md).
     *
     * 중복을 예외로 잡지 않고 ON DUPLICATE KEY UPDATE로 흡수한다. 제약 위반이 나면 트랜잭션이
     * 롤백 전용이 되어, 예외를 잡아 넘겨도 이어지는 집계가 커밋에서 터지기 때문이다.
     *
     * 자기 인증에 누르는 것을 막지 않는다. 막으면 검증 분기와 에러 코드가 늘지만 얻는 것이 적다.
     */
    @Transactional
    public ChallengeResDTO.Like like(Long memberId, Long challengeId, Long verificationId) {
        findVerificationInChallenge(challengeId, verificationId);
        challengeVerificationLikeRepository.insertIfAbsent(verificationId, memberId);
        return buildLikeResult(verificationId, true);
    }

    /**
     * 좋아요 취소(#63). 누르지 않은 상태여도 성공으로 처리한다.
     *
     * 취소는 행 삭제다. 소프트 삭제를 쓰면 취소 후 다시 누를 때 남아 있는 행이 유니크 제약에
     * 걸린다(database-schema.md).
     */
    @Transactional
    public ChallengeResDTO.Like unlike(Long memberId, Long challengeId, Long verificationId) {
        // 없는 인증에 대한 취소는 404로 알린다. 멱등한 것은 "좋아요가 없는 경우"이지
        // "인증이 없는 경우"가 아니다 — 후자는 클라이언트가 잘못된 id를 보낸 것이다.
        findVerificationInChallenge(challengeId, verificationId);
        challengeVerificationLikeRepository.deleteLike(verificationId, memberId);
        return buildLikeResult(verificationId, false);
    }

    /** 경로의 challengeId와 인증의 챌린지가 맞는지까지 확인한다. 어긋나면 404다(신고와 같은 기준). */
    private ChallengeVerification findVerificationInChallenge(Long challengeId, Long verificationId) {
        return challengeVerificationRepository
                .findByIdAndMemberChallengeChallengeId(verificationId, challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.VERIFICATION_NOT_FOUND));
    }

    /**
     * 응답에 최종 상태를 실어 클라이언트가 재조회 없이 화면을 갱신하게 한다.
     * 집계는 피드와 같은 배치 쿼리를 한 건짜리로 부른다 — 세는 규칙(탈퇴 회원 제외)이 갈리지 않도록.
     */
    private ChallengeResDTO.Like buildLikeResult(Long verificationId, boolean liked) {
        long likeCount = challengeVerificationLikeRepository
                .countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L);
        return ChallengeConverter.toLike(verificationId, likeCount, liked);
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
