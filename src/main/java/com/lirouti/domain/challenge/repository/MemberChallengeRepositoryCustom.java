package com.lirouti.domain.challenge.repository;

import java.util.List;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;

public interface MemberChallengeRepositoryCustom {

    /**
     * 회원이 현재 참여 중(active=true)인 챌린지 목록. 최근 참여 순.
     * 비활성(active=false) 챌린지는 제외한다. category가 null이면 전체, keyword가 비면 검색 없음.
     */
    List<Challenge> findMyActiveChallenges(Long memberId, ChallengeCategory category, String keyword);
}
