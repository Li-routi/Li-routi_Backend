package com.lirouti.domain.chat.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaReferenceSource;

import lombok.RequiredArgsConstructor;

/**
 * 채팅 도메인이 참조하는 S3 asset key를 미디어 정리 배치에 제공한다.
 * 미디어 정리 서비스가 채팅 repository를 직접 의존하지 않도록 참조 조회를 이 어댑터에 둔다.
 */
@Component
@RequiredArgsConstructor
public class ChatMediaReferenceSource implements MediaReferenceSource {
    private final ChatEmoticonRepository chatEmoticonRepository;

    @Override
    public Set<MediaPurpose> coveredPurposes() {
        return Set.of(MediaPurpose.CHAT_EMOTICON);
    }

    /**
     * 후보 key 중 chat_emoticon이 참조 중인 key만 반환한다.
     * 비활성 자산도 기존 메시지에서 사용될 수 있어 active 상태와 관계없이 보존한다.
     */
    @Override
    @Transactional(readOnly = true)
    public Set<String> findReferencedKeys(Collection<String> candidateKeys) {
        if (candidateKeys.isEmpty()) {
            return Set.of();
        }

        // 비활성 자산도 기존 메시지가 참조할 수 있으므로 active 조건을 적용하지 않는다.
        return new HashSet<>(chatEmoticonRepository.findAssetKeysIn(candidateKeys));
    }
}
