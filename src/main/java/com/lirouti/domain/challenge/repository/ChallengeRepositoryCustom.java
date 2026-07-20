package com.lirouti.domain.challenge.repository;

import java.time.LocalDate;
import java.util.List;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;

public interface ChallengeRepositoryCustom {

    /**
     * 활성 챌린지 목록을 커서 기반으로 조회한다(무한 스크롤용, 최신순).
     * category가 null이면 전체, keyword가 비어있으면 검색 없음.
     * cursor가 null이면 첫 페이지(가장 최신부터), 값이 있으면 그 challengeId보다 이전(더 오래된) 것만 가져온다.
     */
    List<Challenge> findByCursor(
            ChallengeCategory category,
            String keyword,
            Long cursor,
            int limit
    );

    /**
     * 한 챌린지의 현재 참여자 수. 탈퇴 회원은 제외한다. (상세 조회용)
     */
    long countActiveParticipants(Long challengeId);

    /**
     * 한 챌린지의 오늘 완료자 수. 회원 단위로 중복 제거하고(현재 회차 기준), 탈퇴 회원은 제외한다. (상세 조회용)
     */
    long countTodayCompletions(Long challengeId, LocalDate today);
}
