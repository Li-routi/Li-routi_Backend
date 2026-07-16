package com.lirouti.domain.challenge.repository;

import java.time.LocalDate;
import java.util.List;

import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.ChallengeSortType;

public interface ChallengeRepositoryCustom {

    /**
     * 활성 챌린지 목록을 참여자 수와 함께 조회한다.
     * category가 null이면 전체, keyword가 비어있으면 검색 없음. 정렬은 sort를 따른다.
     * 참여자 수는 활성 참여(active = true) 중 활성 회원만 센다.
     */
    List<ChallengeSummaryProjection> findSummaries(
            ChallengeCategory category,
            String keyword,
            ChallengeSortType sort
    );

    /**
     * 한 챌린지의 현재 참여자 수. 탈퇴 회원은 제외한다.
     */
    long countActiveParticipants(Long challengeId);

    /**
     * 한 챌린지의 오늘 완료자 수. 회원 단위로 중복 제거하고(현재 회차 기준), 탈퇴 회원은 제외한다.
     */
    long countTodayCompletions(Long challengeId, LocalDate today);
}
