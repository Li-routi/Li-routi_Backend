package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;

import java.util.List;
import java.util.Map;

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
     * 여러 챌린지의 현재 참여자 수를 한 번에 집계한다(전체 목록 카드용, N+1 회피).
     * 탈퇴 회원은 제외한다. 참여자 0인 챌린지는 맵에 없다(호출부가 0으로 처리).
     */
    Map<Long, Long> countActiveParticipantsByChallengeIds(List<Long> challengeIds);

    /**
     * 여러 챌린지의 인증 게시글 수를 한 번에 집계한다(전체 목록 카드용, N+1 회피).
     * 인증(게시글) 단위 집계이므로 회차 중복 제거를 하지 않는다. 탈퇴 회원의 인증은 제외한다.
     */
    Map<Long, Long> countVerificationPostsByChallengeIds(List<Long> challengeIds);

    /**
     * 한 챌린지의 현재 참여자 수. 탈퇴 회원은 제외한다. (상세 조회용)
     */
    long countActiveParticipants(Long challengeId);

    /**
     * 한 챌린지의 인증 게시글 수. 인증(게시글) 단위 집계이므로 회차 중복 제거를 하지 않는다.
     * 탈퇴 회원의 인증은 제외한다. (상세 조회용)
     */
    long countVerificationPosts(Long challengeId);

    /**
     * 챌린지별 대표 이미지 후보(좋아요 1위 인증 사진의 S3 key). 목록 카드용 배치 조회다.
     *
     * <p>숨김·보류·삭제·탈퇴 회원 인증은 제외한다. 좋아요가 같으면 최신 인증({@code id} 내림차순)
     * 이 이긴다 — tie-break 가 없으면 요청마다 표지가 바뀐다.
     *
     * <p>인증이 없는 챌린지는 결과에 담기지 않는다.
     */
    Map<Long, String> coverImagesByChallengeIds(List<Long> challengeIds);
}
