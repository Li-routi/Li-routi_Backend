package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long>, ChallengeRepositoryCustom {

    Optional<Challenge> findByIdAndActiveTrue(Long id);

    /**
     * 후보 key 중 챌린지 대표 이미지로 쓰이고 있는 것만 고른다.
     * 미참조 이미지 정리 배치가 이 결과로 삭제 대상을 판단한다.
     *
     * 지금은 {@code image_url}이 전부 비어 있어 항상 빈 결과다(R__seed_challenge.sql이
     * "미디어 서빙 주소가 확정된 뒤에 채운다"고 남겨 뒀다). 그래도 미리 두는 이유는,
     * 나중에 대표 이미지를 채우는 사람이 정리 배치의 존재를 모를 가능성이 높기 때문이다.
     * 비활성 챌린지({@code active = false})도 포함한다 — 행이 남아 있으면 파일도 살아 있다.
     */
    @Query("select c.imageUrl from Challenge c where c.imageUrl in :keys")
    List<String> findImageUrlsIn(@Param("keys") Collection<String> keys);
}
