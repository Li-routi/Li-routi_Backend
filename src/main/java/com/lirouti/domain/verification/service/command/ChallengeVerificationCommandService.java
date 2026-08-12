package com.lirouti.domain.verification.service.command;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.verification.converter.ChallengeVerificationConverter;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.dto.request.ChallengeVerificationReqDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.reward.service.command.RewardCommandService;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
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
 * <b>이 메서드 안의 순서는 그대로 유지해야 한다.</b> 행 락 → 이번 구간 인증 조회 → INSERT/덮어쓰기 →
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
    private final RewardCommandService rewardCommandService;

    /** achievement 도메인이 구독하는 루틴 완료 이벤트의 condition key. 개인 루틴 인증과 동일한 키를 공유한다. */
    private static final String CONDITION_KEY_ROUTINE_COMPLETE_COUNT = "ROUTINE_COMPLETE_COUNT";
    private static final String SOURCE_TYPE_CHALLENGE_VERIFICATION = "CHALLENGE_VERIFICATION";
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 인증 저장과 스트릭 갱신. <b>DAILY</b> 에서 오늘 이미 인증했으면 행을 새로 만들지 않고
     * 덮어쓴다(당일 재인증). 이때 스트릭은 오르지 않는다. 주간·월간은 덮어쓰지 않고 409 다.
     *
     * 인증 INSERT와 스트릭 갱신을 한 트랜잭션에 두는 것이 중복 증가를 막는 핵심이다 —
     * 동시 요청은 UNIQUE(member_challenge_id, participation_round, period_start_date)에 걸려 실패하고,
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

        // 주기는 챌린지가 정한다. 여기서 한 번 읽어 저장·스트릭이 같은 값을 쓰게 한다 —
        // 두 번 읽으면 그 사이 운영이 주기를 바꿨을 때 한 요청 안에서 기준이 갈린다.
        RoutineCycle cycle = memberChallenge.getChallenge().getRoutineCycle();
        LocalDate periodStart = cycle.currentPeriodStart(today);

        // 회차를 빼고 찾는다. 회차를 조건에 넣으면 나갔다 다시 들어온 뒤 같은 구간에 또 인증할
        // 수 있다 — 새 회차에서는 기존 인증이 안 보여 덮어쓰기가 아니라 새 행이 되기 때문이다.
        // 주기 1회는 회차를 넘어 적용한다.
        Optional<ChallengeVerification> periodVerification = challengeVerificationRepository
                .findByMemberChallengeIdAndPeriodStart(memberChallenge.getId(), periodStart);

        // 이번 구간에 이미 인증이 있으면 막는다. <b>주기로 가르지 않는다</b> — 주가 지나지
        // 않았으면 주간도 하루와 똑같이 "이미 낸 것" 이다.
        //
        // 유일한 예외가 본인이 지운 경우다. 지우면 그 구간이 다시 열린다 — 대신 그때 리워드를
        // 회수한다(재화 이슈). "올리고 기록만 챙긴 뒤 지우기" 를 막는 역할은 회수가 가져가고,
        // 여기서는 막지 않는다.
        //
        // 보류(PENDING) 도 막는다. 심사 중이어도 이미 낸 것이고, 통과·반려는 서버가 알아서
        // 정리한다(반려로 확정되면 행을 지우므로 그 시점에 자연히 다시 열린다).
        //
        // 신고로 가려진 글도 여기서 막힌다. 그리고 가려진 글은 지울 수도 없으므로
        // (findMineInChallenge 가 hiddenAt is null 로 거른다) 그 구간은 닫힌 채로 끝난다.
        // 의도한 것이다 — 다시 열어 주면 신고를 받은 사람이 사진만 바꿔 계속 낼 수 있어
        // 숨김이 제재로서 힘을 잃는다.
        //
        // 지난 회차 것이어도 같다. 회차가 올라가도 그 구간에 인증한 사실은 남는다 —
        // 나가기/들어오기로 인증 횟수를 늘릴 수 없다.
        ChallengeVerification occupied = periodVerification
                .filter(v -> !v.isDeleted())
                .orElse(null);
        if (occupied != null) {
            log.info("이번 구간에 이미 인증이 있어 재인증을 막았습니다."
                            + " memberId={}, challengeId={}, cycle={}, periodStart={}, round={}",
                    memberId, challengeId, cycle, periodStart, occupied.getParticipationRound());
            throw new VerificationException(alreadyVerified(cycle));
        }

        // 여기부터는 "지워진 행이 있거나, 아무것도 없거나" 둘 중 하나다.
        //
        // 지워진 행이 <b>지난 회차</b> 것이면 되살리지 않고 새로 만든다. 되살리면 회차가 옛
        // 값으로 남아 "이번 참여의 기록" 이라는 뜻이 어긋난다. 유니크 키에 회차가 들어 있어
        // 새 행을 만들어도 부딪히지 않는다.
        Optional<ChallengeVerification> revivable = periodVerification
                .filter(v -> v.getParticipationRound().equals(memberChallenge.getParticipationRound()));

        boolean reverified = revivable.isPresent();

        ChallengeVerification verification = revivable
                .map(existing -> {
                    existing.reverify(storedMediaKey, request.content(), verifiedAt,
                            reviewStatus, pendingSinceFor(reviewStatus, verifiedAt));
                    return existing;
                })
                .orElseGet(() -> createVerification(memberChallenge, request, storedMediaKey,
                        today, periodStart, verifiedAt, reviewStatus));

        // 재인증이면 applyVerification 이 같은 구간임을 보고 스트릭을 그대로 둔다.
        memberChallenge.applyVerification(today, cycle);

        // 심사를 통과한 인증에만 지급한다. 보류는 아직 통과한 것이 아니므로 여기서 주지 않고,
        // 나중에 재심사가 승인할 때 그 시점에 준다 — 남의 서비스 장애로 보류된 사람만 리워드를
        // 못 받는 상태가 되면 안 된다(스트릭을 그때도 올려 주기로 한 것과 같은 기준이다).
        //
        // 같은 트랜잭션이라야 한다. 갈리면 "인증은 저장됐는데 리워드가 없는" 상태가 조용히
        // 남고, 사용자는 안 들어온 것만 알 뿐 서버는 이유를 모른다.
        //
        // 재인증(되살리기)이면 verification 의 id 가 그대로라, 이미 지급된 건은 유니크 제약과
        // 사전 조회가 함께 막는다 — 사진만 갈아끼우고 또 받는 길이 없다.
        if (!verification.isPending()) {
            challengeVerificationRepository.flush();   // id 가 있어야 지급 대상을 가리킬 수 있다
            rewardCommandService.grantForVerification(
                    memberChallenge.getMember(), verification.getId(),
                    memberChallenge.getChallenge().getReward());

            if (eventPublisher != null) {
                eventPublisher.publishEvent(new AchievementProgressEvent(
                        memberChallenge.getMember().getId(),
                        CONDITION_KEY_ROUTINE_COMPLETE_COUNT,
                        1,
                        SOURCE_TYPE_CHALLENGE_VERIFICATION,
                        verification.getId()
                ));
            }
        }

        // 보류 건은 아직 대기 prefix 에 있어 공개 주소가 없다. 그 주소로 열면 403 이므로
        // 서명을 발급한다 — 본인이 방금 올린 사진이라 여기서 보여 주는 것은 문제가 없다.
        String viewUrl = verification.isPending()
                ? mediaService.presignedViewUrl(verification.getImageUrl())
                : mediaService.resolvePublicUrl(verification.getImageUrl());

        return ChallengeVerificationConverter.toVerification(
                verification,
                viewUrl,
                memberChallenge.currentStreakAsOf(today, cycle),
                reverified
        );
    }

    private ChallengeVerification createVerification(
            MemberChallenge memberChallenge,
            ChallengeVerificationReqDTO.Verify request,
            String storedMediaKey,
            LocalDate verifiedDate,
            LocalDate periodStartDate,
            LocalDateTime verifiedAt,
            ReviewStatus reviewStatus
    ) {
        ChallengeVerification verification = ChallengeVerification.builder()
                .memberChallenge(memberChallenge)
                .participationRound(memberChallenge.getParticipationRound())
                .verifiedDate(verifiedDate)
                .periodStartDate(periodStartDate)
                .verifiedAt(verifiedAt)
                .imageUrl(storedMediaKey)
                .content(request.content())
                .reviewStatus(reviewStatus)
                .pendingSince(pendingSinceFor(reviewStatus, verifiedAt))
                .build();
        try {
            // "이번 구간 인증이 없다"는 선검사와 저장 사이의 동시 요청 경합은 유니크 제약이 막는다.
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
     * "이미 인증했다" 를 알리는 코드. <b>주기마다 문장이 달라야 한다.</b>
     *
     * <p>주간 챌린지에 "오늘은 이미 인증했습니다" 가 나가면 사용자는 내일 다시 눌러 본다 —
     * 실제로는 다음 주까지 기다려야 한다.
     */
    private ChallengeVerificationErrorCode alreadyVerified(RoutineCycle cycle) {
        return cycle == RoutineCycle.DAILY
                ? ChallengeVerificationErrorCode.ALREADY_VERIFIED_TODAY
                : ChallengeVerificationErrorCode.ALREADY_VERIFIED_IN_PERIOD;
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

        // 회수를 먼저 한다. 모자라면 예외가 나가 삭제까지 함께 되돌아간다 — 재화만 빼앗기고
        // 글은 남거나, 글은 지워졌는데 재화는 그대로인 중간 상태를 만들지 않는다.
        //
        // 이미 내려간 글을 다시 지우는 요청이면 지급 행이 첫 삭제 때 사라졌으므로 회수할
        // 것이 없다. 두 번 걷히지 않는다.
        rewardCommandService.clawbackForVerification(memberId, verificationId);

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
