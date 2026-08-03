package com.lirouti.domain.challenge.service.query;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationLikeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChallengeQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final ChallengeVerificationLikeRepository challengeVerificationLikeRepository;
    // 저장된 오브젝트 key를 읽기용 공개 URL로 바꾸기 위해 주입한다(DB를 다루지 않는 유틸성 서비스).
    private final MediaService mediaService;

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

    /**
     * 챌린지의 최신 인증 피드. 닉네임·사진·코멘트를 최신순으로 내려준다.
     *
     * 인증(게시글) 단위 나열이므로 회차 중복을 제거하지 않는다. 같은 날 이탈 후 재참여해 다시
     * 인증한 두 건은 별개의 인증 이벤트다(database-schema.md).
     *
     * viewerId(조회자)가 신고한 인증은 빠진다. 신고가 적을 때는 신고자 본인에게만 가려지므로
     * 같은 인증이 다른 회원의 피드에는 그대로 남는다.
     *
     * 신고가 임계값만큼 쌓이면 조회자가 누구든 빠진다 — 그건 이 조건이 아니라 hiddenAt 이 막는다.
     */
    @Transactional(readOnly = true)
    public ChallengeResDTO.Feed getVerificationFeed(
            Long challengeId,
            Long viewerId,
            Long cursor,
            Integer size
    ) {
        // 없는/내려간 챌린지에 빈 배열 대신 404를 준다. 상세 조회와 같은 기준.
        challengeRepository.findByIdAndActiveTrue(challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.CHALLENGE_NOT_FOUND));

        int appliedSize = clampSize(size);
        List<ChallengeVerification> rows = challengeVerificationRepository
                .findFeedByCursor(challengeId, viewerId, cursor, appliedSize + 1);
        CursorPage<ChallengeVerification> page =
                sliceByCursor(rows, appliedSize, ChallengeVerification::getId);

        // DB에는 오브젝트 key만 있으므로 공개 URL은 여기서 조립해 Converter에 넘긴다.
        Map<Long, String> imageUrls = page.rows().stream()
                .collect(Collectors.toMap(
                        ChallengeVerification::getId,
                        v -> mediaService.resolvePublicUrl(v.getImageUrl())));

        // 좋아요 수와 "내가 눌렀는지"는 이 페이지의 인증 id 목록으로 각각 한 번에 가져온다(#63).
        // 건별로 세거나 exists를 부르면 페이지 크기만큼 쿼리가 늘어난다.
        List<Long> ids = page.rows().stream().map(ChallengeVerification::getId).toList();
        Map<Long, Long> likeCounts = challengeVerificationLikeRepository.countByVerificationIds(ids);
        Set<Long> likedIds =
                challengeVerificationLikeRepository.findLikedVerificationIds(ids, viewerId);

        return ChallengeConverter.toFeed(
                page.rows(), imageUrls, likeCounts, likedIds, viewerId,
                page.nextCursor(), page.hasNext());
    }

    /**
     * 그 챌린지에서 내가 남긴 인증만 커서 기반으로 조회한다(#62).
     *
     * <b>전체 회차를 돌려준다.</b> 예전에는 현재 회차만 담았는데, 재참여하면 지난 회차 인증이
     * 내 목록에서만 사라졌다 — 피드에는 그대로 남고 mine 까지 true 라, 내 글이라고 표시되는데
     * 내 목록엔 없는 상태였다. 회차는 되돌아가지 않아 영영 다시 보이지 않았고, 이탈이 기록을
     * 지우지 않는다는 원칙(database-schema.md)과도 어긋났다.
     *
     * 대신 회차를 응답에 실어 클라이언트가 "이번 참여 / 지난 참여"를 가른다.
     * 스트릭은 그대로 현재 회차 기준이라 목록과 기준이 다르다 — 그래서 회차가 필요하다.
     *
     * 이탈한 챌린지도 조회된다. 이탈은 active만 내리고 회차는 그대로여서, 이탈 상태의
     * 현재 회차는 곧 마지막 참여 기록이다. 그만뒀다고 자기 기록을 못 보게 할 이유가 없다.
     * 다만 한 번도 참여한 적이 없으면 빈 목록이 아니라 NOT_PARTICIPATING으로 돌려준다 —
     * 그 경우는 "기록이 없다"가 아니라 "볼 자격이 없다"에 가깝다.
     *
     * 피드와 달리 챌린지 존재 여부를 따로 확인하지 않는다. 참여 행이 있다는 것이 곧 그 챌린지가
     * 있었다는 뜻이고, 운영이 챌린지를 내려도 이미 남긴 내 기록은 보여야 한다(이탈과 같은 기준).
     *
     * 스크롤 도중 데이터가 바뀌어도 목록이 어긋나지 않는다. 정렬·커서 키가 id이기 때문이다.
     * 새 인증은 커서보다 큰 id라 이미 넘긴 페이지에 끼어들지 않고, 당일 재인증은 같은 행을
     * 갱신하므로(verified_at만 바뀐다) 위치가 흔들리지 않는다.
     *
     * 회차 조건을 뺀 덕에 스크롤 도중 재참여해도 다음 페이지가 비지 않는다. 예전에는 커서가
     * 지난 회차의 id 라 새 회차 인증과 겹치지 않아 목록이 갑자기 끝났다.
     */
    @Transactional(readOnly = true)
    public ChallengeResDTO.MyVerifications getMyVerifications(
            Long memberId,
            Long challengeId,
            Long cursor,
            Integer size
    ) {
        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeId(memberId, challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING));

        int appliedSize = clampSize(size);
        List<ChallengeVerification> rows = challengeVerificationRepository.findMineByCursor(
                memberChallenge.getId(),
                cursor,
                appliedSize + 1
        );
        CursorPage<ChallengeVerification> page =
                sliceByCursor(rows, appliedSize, ChallengeVerification::getId);

        Map<Long, String> imageUrls = page.rows().stream()
                .collect(Collectors.toMap(
                        ChallengeVerification::getId,
                        v -> mediaService.resolvePublicUrl(v.getImageUrl())));

        // 좋아요 수는 피드와 같은 배치 쿼리를 쓴다(#63). 같은 인증이 피드에도 여기에도 나오므로
        // 수가 달라 보이면 안 된다. liked는 싣지 않는다 — 자기 게시물이라 쓸 데가 없다.
        List<Long> ids = page.rows().stream().map(ChallengeVerification::getId).toList();
        Map<Long, Long> likeCounts = challengeVerificationLikeRepository.countByVerificationIds(ids);

        // 스트릭은 저장된 값을 그대로 쓰지 않고 오늘 기준으로 다시 판정한다.
        // 마지막 인증이 이틀 전이면 저장값은 그대로여도 실제로는 끊긴 상태다.
        int currentStreak = memberChallenge.currentStreakAsOf(LocalDate.now(TimeUtil.KST));

        return ChallengeConverter.toMyVerifications(
                page.rows(), imageUrls, likeCounts, currentStreak,
                memberChallenge.getParticipationRound(), page.nextCursor(), page.hasNext());
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

    // memberId는 조회자. 상세도 인증이 필요하므로(#77) 컨트롤러에서 null이 오지 않는다.
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
     * 버튼이 다시 열렸다. 하루 1회는 회차를 넘어 적용하므로 <b>인증 테이블을 직접 본다.</b>
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
        return challengeVerificationRepository.existsByMemberChallengeIdAndVerifiedDateBetween(
                participation.getId(), cycle.currentPeriodStart(today), today);
    }

    private int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
