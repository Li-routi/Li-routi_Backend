package com.lirouti.domain.challenge.service.query;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.challenge.converter.ChallengeConverter;
import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.ChallengeSortType;
import com.lirouti.domain.challenge.exception.ChallengeException;
import com.lirouti.domain.challenge.exception.code.error.ChallengeErrorCode;
import com.lirouti.domain.challenge.repository.ChallengeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeQueryService {
    // 오늘 완료자 수 판단 기준 시각대. 스키마 문서 기준 KST.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChallengeRepository challengeRepository;

    @Transactional(readOnly = true)
    public ChallengeResDTO.Listing getChallenges(
            ChallengeCategory category,
            String keyword,
            ChallengeSortType sort
    ) {
        ChallengeSortType appliedSort = (sort != null) ? sort : ChallengeSortType.POPULAR;
        return ChallengeConverter.toListing(
                challengeRepository.findSummaries(category, keyword, appliedSort)
        );
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
}
