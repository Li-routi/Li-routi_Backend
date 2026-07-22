package com.lirouti.domain.challenge.service.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.global.util.TimeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;
    private final ChallengeVerificationRepository challengeVerificationRepository;
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
     */
    @Transactional(readOnly = true)
    public ChallengeResDTO.Feed getVerificationFeed(Long challengeId, Long cursor, Integer size) {
        // 없는/내려간 챌린지에 빈 배열 대신 404를 준다. 상세 조회와 같은 기준.
        challengeRepository.findByIdAndActiveTrue(challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.CHALLENGE_NOT_FOUND));

        int appliedSize = clampSize(size);
        List<ChallengeVerification> rows =
                challengeVerificationRepository.findFeedByCursor(challengeId, cursor, appliedSize + 1);
        CursorPage<ChallengeVerification> page =
                sliceByCursor(rows, appliedSize, ChallengeVerification::getId);

        // DB에는 오브젝트 key만 있으므로 공개 URL은 여기서 조립해 Converter에 넘긴다.
        Map<Long, String> imageUrls = page.rows().stream()
                .collect(Collectors.toMap(
                        ChallengeVerification::getId,
                        v -> mediaService.resolvePublicUrl(v.getImageUrl())));

        return ChallengeConverter.toFeed(page.rows(), imageUrls, page.nextCursor(), page.hasNext());
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

    @Transactional(readOnly = true)
    public ChallengeResDTO.Detail getChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findByIdAndActiveTrue(challengeId)
                .orElseThrow(() -> new ChallengeException(ChallengeErrorCode.CHALLENGE_NOT_FOUND));

        long participantCount = challengeRepository.countActiveParticipants(challengeId);
        long todayCompletionCount =
                challengeRepository.countTodayCompletions(challengeId, LocalDate.now(TimeUtil.KST));

        return ChallengeConverter.toDetail(challenge, participantCount, todayCompletionCount);
    }

    private int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
