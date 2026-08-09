package com.lirouti.domain.verification.service.command;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.verification.converter.ChallengeVerificationConverter;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.ChallengeVerificationErrorCode;

/**
 * 인증의 <b>저장 단계</b>만 담당한다. 트랜잭션 경계가 이 클래스에 있다.
 *
 * {@code ChallengeVerificationService#verify}에서 분리한 이유는 트랜잭션 밖에서 끝내야 하는 일이
 * 앞에 있기 때문이다(업로드 바이트 검증, 이어서 AI 심사). 두 단계를 한 메서드에 두면
 * 외부 API 호출이 트랜잭션 안으로 들어가 DB 커넥션과 행 락을 그 시간만큼 붙잡는다
 * (service_convention: 트랜잭션 내 장시간 외부 API 호출 금지).
 *
 * 같은 클래스 안에서 메서드를 나누는 것으로는 안 된다 — 자기 호출은 프록시를 거치지 않아
 * 트랜잭션이 걸리지 않는다(service_convention). 그래서 빈을 분리했다.
 * {@code AuthService}가 외부 인증 후 {@code MemberCommandService}에 저장을 위임하는 것과 같은 모양이다.
 *
 * <b>이 메서드 안의 순서는 그대로 유지해야 한다.</b> 행 락 → 오늘 인증 조회 → INSERT/덮어쓰기 →
 * 스트릭 갱신이 한 트랜잭션에 있어야 중복 증가와 stale update가 모두 막힌다(database-schema.md).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeVerificationCommandService {
    private final MemberChallengeRepository memberChallengeRepository;
    private final ChallengeVerificationRepository challengeVerificationRepository;
    // 저장된 key를 공개 URL로 조립하는 데만 쓴다. DB를 다루지 않는 유틸성 서비스다.
    private final MediaService mediaService;

    /**
     * 인증 저장과 스트릭 갱신. 오늘 이미 인증했으면 행을 새로 만들지 않고 덮어쓴다(당일 재인증).
     * 이때 스트릭은 오르지 않는다.
     *
     * 인증 INSERT와 스트릭 갱신을 한 트랜잭션에 두는 것이 중복 증가를 막는 핵심이다 —
     * 동시 요청은 UNIQUE(member_challenge_id, participation_round, verified_date)에 걸려 실패하고,
     * 그 예외로 트랜잭션 전체가 롤백되어 스트릭 갱신도 함께 되돌아간다.
     *
     * 비활성 챌린지라도 참여 중이면 인증할 수 있다. 운영이 챌린지를 내려도 진행 중인 스트릭이
     * 끊기지 않게 하기 위해서이며, 이탈(leave)이 챌린지 active를 보지 않는 것과 같은 기준이다.
     * <p><b>{@code storedMediaKey} 는 요청의 key 가 아니다.</b> 챌린지 인증 사진은 비공개 대기
     * prefix 로 업로드받아 심사를 통과한 뒤 공개 prefix 로 승격되므로, DB 에 남기고 응답에 실을
     * 것은 승격된 공개 key 다. 승격은 S3 호출이라 이 트랜잭션 밖(호출부)에서 끝내고 결과만 받는다.
     *
     */
    @Transactional
    public ChallengeVerificationResDTO.Verification save(
            Long memberId,
            Long challengeId,
            ChallengeVerificationReqDTO.Verify request,
            String storedMediaKey,
            ReviewStatus reviewStatus
    ) {
        // 이탈·재참여와 같은 행을 바꾸므로 잠그고 읽는다. 락 없이 읽으면 이 트랜잭션이 커밋할 때
        // 그 사이 커밋된 이탈·재참여 결과를 오래된 스냅샷으로 되돌린다.
        // 회차(participation_round)를 읽어 인증 행에 심으므로, 잠그지 않으면 이미 바뀐 회차를
        // 모르고 옛 회차로 인증을 저장한다 — 유니크 제약에도 걸리지 않아 조용히 어긋난다.
        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeIdForUpdate(memberId, challengeId)
                .filter(MemberChallenge::isParticipating)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING));

        // 기준일과 인증 시각을 같은 순간에서 뽑는다. now()를 두 번 부르면 자정 경계에서
        // 날짜와 시각이 서로 다른 날을 가리킬 수 있다.
        ZonedDateTime now = ZonedDateTime.now(TimeUtil.KST);
        LocalDate today = now.toLocalDate();
        LocalDateTime verifiedAt = now.toLocalDateTime();

        // 회차를 빼고 찾는다. 회차를 조건에 넣으면 나갔다 다시 들어온 뒤 같은 날 또 인증할 수
        // 있다 — 새 회차에서는 기존 인증이 안 보여 덮어쓰기가 아니라 새 행이 되기 때문이다.
        // 하루 1회는 회차를 넘어 적용한다.
        Optional<ChallengeVerification> todayVerification = challengeVerificationRepository
                .findByMemberChallengeIdAndVerifiedDate(memberChallenge.getId(), today);

        // 지난 회차에 오늘 인증한 것이면 덮어쓰지 않고 막는다.
        //
        // 덮어쓰면 그 인증이 지난 참여의 기록인데 오늘 올린 사진으로 바뀌고, 회차와 내용이
        // 어긋난다. 새로 만들면 하루 두 건이 되어 애초에 막으려던 것이 된다. 남는 선택은
        // 거절뿐이다 — 이미 오늘 했으므로 "다시 할 수 없다"가 맞다.
        boolean fromPreviousRound = todayVerification
                .filter(v -> v.getParticipationRound() < memberChallenge.getParticipationRound())
                .isPresent();
        if (fromPreviousRound) {
            log.warn("지난 회차에 오늘 인증한 이력이 있어 재인증을 막았습니다."
                            + " memberId={}, challengeId={}, currentRound={}",
                    memberId, challengeId, memberChallenge.getParticipationRound());
            throw new VerificationException(ChallengeVerificationErrorCode.ALREADY_VERIFIED_TODAY);
        }

        boolean reverified = todayVerification.isPresent();

        ChallengeVerification verification = todayVerification
                .map(existing -> {
                    existing.reverify(storedMediaKey, request.content(), verifiedAt,
                            reviewStatus, pendingSinceFor(reviewStatus, verifiedAt));
                    return existing;
                })
                .orElseGet(() -> createVerification(
                        memberChallenge, request, storedMediaKey, today, verifiedAt, reviewStatus));

        // 재인증이면 applyVerification이 오늘 날짜를 보고 스트릭을 그대로 둔다.
        memberChallenge.applyVerification(today);

        // 보류 건은 아직 대기 prefix 에 있어 공개 주소가 없다. 그 주소로 열면 403 이므로
        // 서명을 발급한다 — 본인이 방금 올린 사진이라 여기서 보여 주는 것은 문제가 없다.
        String viewUrl = verification.isPending()
                ? mediaService.presignedViewUrl(verification.getImageUrl())
                : mediaService.resolvePublicUrl(verification.getImageUrl());

        return ChallengeVerificationConverter.toVerification(
                verification,
                viewUrl,
                memberChallenge.currentStreakAsOf(today),
                reverified
        );
    }

    private ChallengeVerification createVerification(
            MemberChallenge memberChallenge,
            ChallengeVerificationReqDTO.Verify request,
            String storedMediaKey,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt,
            ReviewStatus reviewStatus
    ) {
        ChallengeVerification verification = ChallengeVerification.builder()
                .memberChallenge(memberChallenge)
                .participationRound(memberChallenge.getParticipationRound())
                .verifiedDate(verifiedDate)
                .verifiedAt(verifiedAt)
                .imageUrl(storedMediaKey)
                .content(request.content())
                .reviewStatus(reviewStatus)
                .pendingSince(pendingSinceFor(reviewStatus, verifiedAt))
                .build();
        try {
            // "오늘 인증이 없다"는 선검사와 저장 사이의 동시 요청 경합은 유니크 제약이 막는다.
            // saveAndFlush로 그 실패를 여기서 잡아 409로 바꾼다.
            //
            // 두 예외를 모두 잡는다. 같은 유니크 키로 INSERT가 겹칠 때 InnoDB는 늘 중복 키 오류
            // (DataIntegrityViolationException)를 주지 않는다. 중복을 만난 쪽이 기존 인덱스 레코드에
            // 락을 요청하면서 데드락으로 판정되면 CannotAcquireLockException으로 올라온다.
            // 둘 다 "같은 날 인증 경합에서 졌다"는 같은 의미이고, 어느 쪽이든 이 트랜잭션은 롤백된다.
            //
            // 예외를 잡되 삼키지는 않는다. 여기서 던지는 ChallengeException이 트랜잭션을 롤백시켜
            // 위의 스트릭 갱신까지 함께 되돌린다. 만약 "이미 인증했으니 무시하고 진행"으로 처리하면
            // 스트릭 갱신만 커밋되어 값이 두 번 오른다(database-schema.md).
            //
            // 재시도하지 않는 이유: 재시도는 먼저 들어온 요청의 인증을 덮어쓰게 된다.
            // 같은 사용자의 중복 클릭이므로 409로 알리는 편이 정직하다.
            return challengeVerificationRepository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            throw new VerificationException(ChallengeVerificationErrorCode.VERIFICATION_CONFLICT);
        }
    }

    /**
     * 보류 시작 시각. <b>인증 시각을 그대로 쓴다.</b>
     *
     * <p>따로 {@code now()} 를 부르지 않는 이유는 기준일·인증 시각과 같은 순간에서 뽑아야
     * 하기 때문이다. 자정 경계에서 두 값이 다른 날을 가리키면 상한 계산이 하루 어긋난다.
     */
    private LocalDateTime pendingSinceFor(ReviewStatus reviewStatus, LocalDateTime verifiedAt) {
        return reviewStatus == ReviewStatus.PENDING ? verifiedAt : null;
    }

    /**
     * 글을 내린다.
     *
     * <p><b>스트릭은 건드리지 않는다.</b> 삭제는 "글을 내리는 것" 이지 "인증을 취소하는 것" 이
     * 아니다 — 사진을 올려 심사를 통과했다면 그 사람은 루틴을 실제로 했고, 공개를 원치 않아
     * 내렸다고 "며칠째 이어왔다" 는 사실까지 부정할 이유는 약하다.
     *
     * <p>"올리고 기록만 챙긴 뒤 지우기" 는 <b>재화 회수가 막는다</b>(재화 이슈). 회수는 시간과
     * 무관해 한 달 전 글을 지워도 적용되는 반면, 스트릭을 시간 기준으로 깎는 방식은 그만큼
     * 기다리면 우회된다 — 어느 값을 잡아도 마찬가지다.
     *
     * <p>이미 내려간 글이면 아무것도 하지 않고 성공으로 답한다. 삭제는 멱등한 편이 클라이언트가
     * 다루기 쉽다.
     */
    @Transactional
    public DeleteResult softDelete(Long memberId, Long challengeId, Long verificationId) {
        ChallengeVerification verification = challengeVerificationRepository
                .findMineInChallenge(verificationId, challengeId, memberId)
                .orElseThrow(() -> {
                    // 남의 글도, 없는 글도, 신고로 가려진 글도 같은 404 다. 가려진 글을 지워
                    // 신고 누적을 회피하는 길도 함께 막힌다.
                    log.warn("삭제할 수 없는 인증입니다. memberId={}, challengeId={}, verificationId={}",
                            memberId, challengeId, verificationId);
                    return new VerificationException(ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND);
                });

        // 참여 행은 잠그지 않는다. member_challenge 를 바꾸지 않으므로 잠글 이유가 없고,
        // 참여 중인지도 보지 않는다 — 이탈해도 인증은 피드에 남으므로 지난 참여의 글도 내릴 수
        // 있어야 한다. 못 지우게 하면 나간 사람의 사진이 계속 공개된 채로 남는다.

        // 인증 행은 잠그고 다시 읽는다. 위 조회는 소유자·챌린지를 확인하는 용도라 잠금이 없어,
        // 그 사이 당일 재인증이 사진을 갈아끼우면 여기서 옛 key 를 들고 나간다. 그러면 S3 에서
        // 지워지는 것은 옛 사진이고, 내려간 글의 현재 사진은 공개 prefix 에 그대로 남는다.
        // 신고가 같은 방식으로 잠그고 다시 읽는다.
        ChallengeVerification locked = challengeVerificationRepository
                .findByIdForUpdate(verificationId)
                .orElseThrow(() -> new VerificationException(
                        ChallengeVerificationErrorCode.VERIFICATION_NOT_FOUND));

        String imageKey = locked.getImageUrl();
        return new DeleteResult(locked.softDelete(LocalDateTime.now(TimeUtil.KST)), imageKey);
    }

    /**
     * 삭제 결과.
     *
     * @param deletedNow 이번 호출로 실제 내려갔는가. 이미 내려가 있었으면 false
     * @param imageKey   내린 글이 들고 있던 사진 key
     */
    public record DeleteResult(boolean deletedNow, String imageKey) {
    }
}
