package com.lirouti.domain.verification.service.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.verification.converter.ChallengeVerificationConverter;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.enums.VerificationSort;
import com.lirouti.domain.verification.repository.ChallengeVerificationLikeRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.global.util.TimeUtil;

import lombok.RequiredArgsConstructor;

/**
 * 챌린지 인증 조회 — 공개 피드와 내 인증 목록.
 *
 * <p>챌린지 자체의 목록·상세는 {@code ChallengeQueryService} 에 남는다. 상세가 "이번 주기에
 * 인증했는지"를 위해 인증을 읽지만 그것은 <b>상세 응답의 일부</b>라, 조회를 이쪽으로 넘기면
 * 서비스끼리 부르는 사슬만 늘고 얻는 것이 없다.
 */
@Service
@RequiredArgsConstructor
public class ChallengeVerificationQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final ChallengeVerificationLikeRepository challengeVerificationLikeRepository;
    // 저장된 오브젝트 key를 읽기용 공개 URL로 바꾸기 위해 주입한다(DB를 다루지 않는 유틸성 서비스).
    private final MediaService mediaService;

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
    public ChallengeVerificationResDTO.Feed getVerificationFeed(
            Long challengeId,
            Long viewerId,
            Long cursor,
            Long cursorLikeCount,
            Integer size,
            VerificationSort sort
    ) {
        // 없는/내려간 챌린지에 빈 배열 대신 404를 준다. 상세 조회와 같은 기준.
        challengeRepository.findByIdAndActiveTrue(challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.CHALLENGE_NOT_FOUND));

        int appliedSize = clampSize(size);
        CursorPage<ChallengeVerification> page = fetchPage(sort, appliedSize,
                () -> challengeVerificationRepository
                        .findFeedByCursor(challengeId, viewerId, cursor, appliedSize + 1),
                () -> challengeVerificationRepository.findFeedByLikes(
                        challengeId, viewerId, cursorLikeCount, cursor, appliedSize + 1));

        // DB에는 오브젝트 key만 있으므로 공개 URL은 여기서 조립해 Converter에 넘긴다.
        // 보류 건은 아직 대기 prefix 에 있어 공개 주소가 없다 — 그 주소로 열면 403 이다.
        // 이 조회는 memberId 로 좁혀 본인 것만 담으므로, 여기서만 서명을 붙인다.
        Map<Long, String> imageUrls = page.rows().stream()
                .collect(Collectors.toMap(
                        ChallengeVerification::getId,
                        v -> v.isPending()
                                ? mediaService.presignedViewUrl(v.getImageUrl())
                                : mediaService.resolvePublicUrl(v.getImageUrl())));

        // 좋아요 수와 "내가 눌렀는지"는 이 페이지의 인증 id 목록으로 각각 한 번에 가져온다.
        // 건별로 세거나 exists를 부르면 페이지 크기만큼 쿼리가 늘어난다.
        List<Long> ids = page.rows().stream().map(ChallengeVerification::getId).toList();
        Map<Long, Long> likeCounts = challengeVerificationLikeRepository.countByVerificationIds(ids);
        Set<Long> likedIds =
                challengeVerificationLikeRepository.findLikedVerificationIds(ids, viewerId);

        return ChallengeVerificationConverter.toFeed(
                page.rows(), imageUrls, likeCounts, likedIds, viewerId,
                page.nextCursor(), nextLikeCursor(sort, page, likeCounts), page.hasNext());
    }

    /**
     * 그 챌린지에서 내가 남긴 인증만 커서 기반으로 조회한다.
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
    public ChallengeVerificationResDTO.MyVerifications getMyVerifications(
            Long memberId,
            Long challengeId,
            Long cursor,
            Long cursorLikeCount,
            Integer size,
            ReviewStatus statusFilter,
            VerificationSort sort
    ) {
        MemberChallenge memberChallenge = memberChallengeRepository
                .findByMemberIdAndChallengeId(memberId, challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.NOT_PARTICIPATING));

        int appliedSize = clampSize(size);
        CursorPage<ChallengeVerification> page = fetchPage(sort, appliedSize,
                () -> challengeVerificationRepository.findMineByCursor(
                        memberChallenge.getId(), cursor, appliedSize + 1, statusFilter),
                () -> challengeVerificationRepository.findMineByLikes(
                        memberChallenge.getId(), cursorLikeCount, cursor,
                        appliedSize + 1, statusFilter));

        // 보류 건은 아직 대기 prefix 에 있어 공개 주소가 없다 — 그 주소로 열면 403 이다.
        // 이 조회는 memberId 로 좁혀 본인 것만 담으므로, 여기서만 서명을 붙인다.
        Map<Long, String> imageUrls = page.rows().stream()
                .collect(Collectors.toMap(
                        ChallengeVerification::getId,
                        v -> v.isPending()
                                ? mediaService.presignedViewUrl(v.getImageUrl())
                                : mediaService.resolvePublicUrl(v.getImageUrl())));

        // 좋아요 수는 피드와 같은 배치 쿼리를 쓴다. 같은 인증이 피드에도 여기에도 나오므로
        // 수가 달라 보이면 안 된다. liked는 싣지 않는다 — 자기 게시물이라 쓸 데가 없다.
        List<Long> ids = page.rows().stream().map(ChallengeVerification::getId).toList();
        Map<Long, Long> likeCounts = challengeVerificationLikeRepository.countByVerificationIds(ids);

        // 스트릭은 저장된 값을 그대로 쓰지 않고 오늘 기준으로 다시 판정한다.
        // 마지막 인증이 직전 구간보다 오래됐으면 저장값은 그대로여도 실제로는 끊긴 상태다.
        int currentStreak = memberChallenge.currentStreakAsOf(
                LocalDate.now(TimeUtil.KST), memberChallenge.getChallenge().getRoutineCycle());

        return ChallengeVerificationConverter.toMyVerifications(
                page.rows(), imageUrls, likeCounts, currentStreak,
                memberChallenge.getParticipationRound(), page.nextCursor(),
                nextLikeCursor(sort, page, likeCounts), page.hasNext());
    }

    /**
     * 정렬에 맞는 한 페이지를 만든다.
     *
     * <p><b>둘의 페이징 방식이 다르다.</b> 최신순은 {@code size + 1} 로 받아 다음 페이지가
     * 있는지 보고 커서를 내려 주지만, 좋아요순은 <b>상위 N개로 끝</b>이라 커서가 없다.
     *
     * <p>그래도 <b>응답 형태는 같게</b> 둔다 — 좋아요순도 {@code nextCursor} 가 {@code null},
     * {@code hasNext} 가 {@code false} 로 나가므로 클라이언트는 하던 대로 커서 규약을 따르면
     * 자연히 한 페이지에서 멈춘다. 나중에 페이징을 붙여도 계약이 깨지지 않는다.
     */
    private CursorPage<ChallengeVerification> fetchPage(
            VerificationSort sort,
            int size,
            Supplier<List<ChallengeVerification>> byLatest,
            Supplier<List<ChallengeVerification>> byLikes
    ) {
        List<ChallengeVerification> rows =
                (sort == VerificationSort.LIKES) ? byLikes.get() : byLatest.get();
        return sliceByCursor(rows, size, ChallengeVerification::getId);
    }

    /**
     * 좋아요순 커서의 두 번째 값. <b>다음 요청은 {@code nextCursor}(id) 와 이 값을 함께</b>
     * 보내야 한다 — 좋아요 수는 0 이 많아 겹치므로 id 하나로는 좌표가 안 잡힌다.
     *
     * <p>이번 페이지 <b>마지막 항목의 좋아요 수</b>다. 표시용 집계에서 그대로 가져오므로
     * 화면에 보이는 수와 커서가 같은 값을 쓴다 — 갈리면 다음 페이지가 어긋난다.
     *
     * <p>최신순이거나 다음 페이지가 없으면 {@code null} 이다.
     */
    private Long nextLikeCursor(
            VerificationSort sort,
            CursorPage<ChallengeVerification> page,
            Map<Long, Long> likeCounts
    ) {
        if (sort != VerificationSort.LIKES || !page.hasNext() || page.nextCursor() == null) {
            return null;
        }
        return likeCounts.getOrDefault(page.nextCursor(), 0L);
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

    private int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
