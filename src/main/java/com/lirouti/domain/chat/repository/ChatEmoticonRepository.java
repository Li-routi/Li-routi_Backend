package com.lirouti.domain.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lirouti.domain.chat.entity.ChatEmoticon;

public interface ChatEmoticonRepository extends JpaRepository<ChatEmoticon, Long> {

    /**
     * 신규 메시지에 사용할 수 있는 활성 이모티콘을 표시 순서대로 조회한다.
     */
    List<ChatEmoticon> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<ChatEmoticon> findAllByOrderByDisplayOrderAscIdAsc();

    boolean existsByCode(String code);

    /**
     * 메시지 전송 시 활성 상태인 이모티콘 코드만 선택할 수 있도록 조회한다.
     */
    Optional<ChatEmoticon> findByCodeAndActiveTrue(String code);

    /**
     * S3에서 조회된 key 중 이모티콘 자산으로 참조 중인 key를 조회한다.
     * 비활성 자산도 기존 메시지가 참조할 수 있으므로 active 조건을 적용하지 않는다.
     */
    @Query("select emoticon.assetKey from ChatEmoticon emoticon where emoticon.assetKey in :assetKeys")
    List<String> findAssetKeysIn(@Param("assetKeys") Collection<String> assetKeys);
}
