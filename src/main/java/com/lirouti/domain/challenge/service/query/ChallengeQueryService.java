package com.lirouti.domain.challenge.service.query;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.MemberChallengeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeQueryService {
    // 오늘 완료자 수 판단 기준 시각대. 스키마 문서 기준 KST.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ChallengeRepository challengeRepository;
    private final MemberChallengeRepository memberChallengeRepository;

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

        boolean hasNext = rows.size() > appliedSize;
        List<Challenge> pageRows = hasNext ? rows.subList(0, appliedSize) : rows;

        // 다음 커서는 이번 페이지 마지막 항목의 id. 더 없으면 null을 내려 클라이언트가 요청을 멈추게 한다.
        Long nextCursor = (hasNext && !pageRows.isEmpty())
                ? pageRows.get(pageRows.size() - 1).getId()
                : null;

        // 카드 통계는 이번 페이지의 챌린지들만 한 번에 배치 집계한다(챌린지별 개별 조회 = N+1 회피).
        List<Long> ids = pageRows.stream().map(Challenge::getId).toList();
        Map<Long, Long> participantCounts = challengeRepository.countActiveParticipantsByChallengeIds(ids);
        Map<Long, Long> verificationCounts = challengeRepository.countVerificationPostsByChallengeIds(ids);

        return ChallengeConverter.toListing(pageRows, participantCounts, verificationCounts, nextCursor, hasNext);
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
                challengeRepository.countTodayCompletions(challengeId, LocalDate.now(KST));

        return ChallengeConverter.toDetail(challenge, participantCount, todayCompletionCount);
    }

    private int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
