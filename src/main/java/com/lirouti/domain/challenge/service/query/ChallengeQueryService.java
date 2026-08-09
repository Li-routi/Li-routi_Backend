package com.lirouti.domain.challenge.service.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.global.util.TimeUtil;

import lombok.RequiredArgsConstructor;

/**
 * 챌린지 목록·내 참여 목록·상세 조회.
 *
 * <p><b>인증 피드와 내 인증 목록은 여기 없다.</b>
 * {@code verification} 도메인의 {@code ChallengeVerificationQueryService} 가 소유한다.
 *
 * <p>다만 상세는 "이번 주기에 인증했는지"를 위해 인증 테이블을 직접 읽는다. 그 값은
 * <b>상세 응답의 일부</b>이고, 조회를 인증 서비스에 넘기면 서비스끼리 부르는 사슬만 늘어난다
 * (service_convention: Service 간 연쇄 호출은 최소화한다).
 */
@Service
@RequiredArgsConstructor
public class ChallengeQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final ChallengeVerificationRepository challengeVerificationRepository;

    @Transactional(readOnly = true)
    public ChallengeResDTO.Listing getChallenges(
            ChallengeCategory category,
            String keyword,
            Long cursor,
            Integer size
    ) {
        int appliedSize = clampSize(size);

        // hasNext 판단을 위해 한 건 더 가져온다(size + 1). 초과분이 있으면 다음 페이지가 있는 것.
        List<Challenge> rows = challengeRepository.findByCursor(
                category, keyword, cursor, appliedSize + 1);
        CursorPage<Challenge> page = sliceByCursor(rows, appliedSize, Challenge::getId);

        // 카드 통계는 이번 페이지의 챌린지들만 한 번에 배치 집계한다(챌린지별 개별 조회 = N+1 회피).
        List<Long> ids = page.rows().stream().map(Challenge::getId).toList();
        Map<Long, Long> participantCounts = challengeRepository.countActiveParticipantsByChallengeIds(ids);
        Map<Long, Long> verificationCounts = challengeRepository.countVerificationPostsByChallengeIds(ids);

        return ChallengeConverter.toListing(
                page.rows(), participantCounts, verificationCounts, page.nextCursor(), page.hasNext());
    }

    /** size + 1로 받아온 행에서 현재 페이지·다음 커서·다음 페이지 여부를 뽑아낸 결과. */
    private record CursorPage<T>(List<T> rows, Long nextCursor, boolean hasNext) {
    }

    /**
     * 커서 페이지네이션 공통 처리.
     * 다음 커서는 이번 페이지 마지막 항목의 id다. 더 없으면 null을 내려 클라이언트가 요청을 멈추게 한다.
     */
    private static <T> CursorPage<T> sliceByCursor(List<T> rows, int size, Function<T, Long> idExtractor) {
        boolean hasNext = rows.size() > size;
        List<T> pageRows = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = (hasNext && !pageRows.isEmpty())
                ? idExtractor.apply(pageRows.get(pageRows.size() - 1))
                : null;
        return new CursorPage<>(pageRows, nextCursor, hasNext);
    }

    // 내가 참여 중인 챌린지 목록(홈 화면). 참여 중인 것만이라 페이지네이션 없이 전부 내려준다.
    @Transactional(readOnly = true)
    public ChallengeResDTO.MyListing getMyChallenges(
            Long memberId,
            ChallengeCategory category,
            String keyword
    ) {
        List<Challenge> challenges =
                memberChallengeRepository.findMyActiveChallenges(memberId, category, keyword);
        return ChallengeConverter.toMyListing(challenges);
    }

    // memberId는 조회자. 상세도 인증이 필요하므로 컨트롤러에서 null이 오지 않는다.
    // 그래도 null을 견디게 둔다 — 방어를 전 계층에서 동시에 지우면 정책이 다시 바뀔 때 NPE로 터진다.
    @Transactional(readOnly = true)
    public ChallengeResDTO.Detail getChallenge(Long challengeId, Long memberId) {
        Challenge challenge = challengeRepository.findByIdAndActiveTrue(challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.CHALLENGE_NOT_FOUND));

        // 참여 행을 한 번만 읽어 참여 여부와 "이번 구간에 인증했는지"를 함께 판정한다.
        // 두 번 조회하면 그 사이에 이탈·재참여가 끼어들어 두 값이 서로 다른 회차를 볼 수 있다.
        Optional<MemberChallenge> participation = (memberId == null)
                ? Optional.empty()
                : memberChallengeRepository.findByMemberIdAndChallengeId(memberId, challengeId);

        boolean participating = participation.filter(MemberChallenge::isParticipating).isPresent();
        boolean verifiedInCurrentPeriod = participation
                .map(mc -> hasVerifiedInCurrentPeriod(mc, challenge.getRoutineCycle()))
                .orElse(false);

        long participantCount = challengeRepository.countActiveParticipants(challengeId);
        long verificationPostCount = challengeRepository.countVerificationPosts(challengeId);
        long todayCompletionCount =
                challengeRepository.countTodayCompletions(challengeId, LocalDate.now(TimeUtil.KST));

        return ChallengeConverter.toDetail(challenge, participating, verifiedInCurrentPeriod,
                participantCount, verificationPostCount, todayCompletionCount);
    }

    /**
     * 현재 주기 구간에 이미 인증했는지.
     *
     * <p><b>참여 행의 {@code lastVerifiedDate} 를 쓰지 않는다.</b> 재참여가 그 값을 {@code null}
     * 로 초기화하기 때문이다 — 오늘 인증한 뒤 나갔다 다시 들어오면 "아직 안 함"으로 보여
     * 버튼이 다시 열렸다. 주기 1회는 회차를 넘어 적용하므로 <b>인증 테이블을 직접 본다.</b>
     *
     * <p>쓰기 쪽도 같은 기준으로 막는다(ChallengeVerificationCommandService). 한쪽만 고치면
     * 버튼은 잠겨 있는데 API 로는 되거나, 그 반대가 된다.
     *
     * <p>구간 경계는 {@link RoutineCycle} 이 안다 — {@code DAILY} 면 같은 날인지,
     * {@code WEEKLY} 면 같은 주(일~토)인지, {@code MONTHLY} 면 같은 달인지.
     * <b>구간 전체를 조회한다.</b> 그날 하나만 보면 {@code WEEKLY} 챌린지를 이번 주 월요일에
     * 인증하고 화요일에 열었을 때 "아직 안 함"이 되어 버튼이 다시 열린다.
     *
     * <p><b>이탈했으면 false 다.</b> 참여 행이 남아 있어도 지금 참여 중이 아니면 인증할 수 없다.
     *
     * <p>신고로 가려진 인증도 인증한 것으로 센다. 숨김은 노출만 막을 뿐 수행 기록은 그대로이며,
     * 스트릭·오늘 완료자 수가 숨김을 무시하는 것과 같은 기준이다.
     */
    private boolean hasVerifiedInCurrentPeriod(MemberChallenge participation, RoutineCycle cycle) {
        if (!participation.isParticipating()) {
            return false;
        }
        LocalDate today = LocalDate.now(TimeUtil.KST);
        return challengeVerificationRepository.existsByMemberChallengeIdAndPeriodStartDate(
                participation.getId(), cycle.currentPeriodStart(today));
    }

    private int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
