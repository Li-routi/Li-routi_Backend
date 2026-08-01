package com.lirouti.domain.challenge.service.query;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
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
     * 현재 회차만 돌려준다. 이탈 후 재참여하면 회차가 오르고 지난 회차 인증이 그대로 남는데,
     * 그것까지 섞으면 스트릭은 0인데 목록에는 지난 참여의 기록이 쌓여 있는 화면이 된다.
     * 인증·연속 참여일·오늘 완료 여부를 모두 현재 회차로 판단하는 기준과 맞춘 것이다
     * (database-schema.md). 화면이 "이번 참여"가 아니라 "전체 내 기록"으로 확정되면 회차 조건만 빼면 된다.
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
     * 다만 스크롤 도중 이탈·재참여가 일어나면 다음 페이지부터 회차가 달라져 빈 목록이 된다.
     * 커서(이전 회차의 id)와 새 회차의 인증이 겹치지 않기 때문이다. 목록이 갑자기 끝날 뿐
     * 중복·유출은 없고, 그 순간에 스크롤하고 있어야 하는 드문 조합이라 별도 처리를 두지 않았다.
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
                memberChallenge.getParticipationRound(),
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
                page.rows(), imageUrls, likeCounts, currentStreak, page.nextCursor(), page.hasNext());
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

        boolean participating = isParticipating(memberId, challengeId);
        long participantCount = challengeRepository.countActiveParticipants(challengeId);
        long verificationPostCount = challengeRepository.countVerificationPosts(challengeId);
        long todayCompletionCount =
                challengeRepository.countTodayCompletions(challengeId, LocalDate.now(TimeUtil.KST));

        return ChallengeConverter.toDetail(
                challenge, participating, participantCount, verificationPostCount, todayCompletionCount);
    }

    // memberId가 null이면 참여 중이 아닌 것으로 본다(위 getChallenge 주석 참고). 그 외에는 현재 참여 상태를 본다.
    private boolean isParticipating(Long memberId, Long challengeId) {
        if (memberId == null) {
            return false;
        }
        return memberChallengeRepository.findByMemberIdAndChallengeId(memberId, challengeId)
                .filter(MemberChallenge::isParticipating)
                .isPresent();
    }

    private int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
