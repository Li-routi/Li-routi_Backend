package com.lirouti.domain.challenge.service;

import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.challenge.repository.ChallengeVerificationRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaReferenceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 챌린지 도메인이 쓰고 있는 미디어 key를 미디어 정리 배치에 알려준다.
 *
 * <p><b>여기 빠뜨린 컬럼이 있으면 그 컬럼이 가리키는 파일이 지워진다.</b> 챌린지 쪽에
 * 미디어 key를 담는 컬럼을 새로 만들면 이 클래스에도 반드시 추가한다.
 *
 * <p>현재 보는 컬럼은 둘이다.
 * <ul>
 *   <li>{@code challenge_verification.image_url} — 사용자가 올린 인증 사진. 정리의 실제 대상이다.</li>
 *   <li>{@code challenge.image_url} — 챌린지 대표 이미지. 지금은 전부 비어 있지만
 *       (R__seed_challenge.sql이 "미디어 서빙 주소가 확정된 뒤에 채운다"고 남겨둠) 나중에 채워질 때
 *       같은 버킷을 쓰면 정리 대상으로 잘못 잡힌다. 그때 이 파일을 고치는 걸 잊기 쉬우니
 *       미리 넣어 둔다 — 조회 한 번 더 도는 비용으로 사고 하나를 막는다.</li>
 * </ul>
 *
 * <p><b>탈퇴·신고로 숨겨진 인증도 참조로 친다.</b> 행이 남아 있으면 key도 살아 있는 것이다.
 * 탈퇴 회원의 사진을 지우는 것은 이 배치가 아니라 별도 정책이 할 일이다.
 *
 * <p>조회 전용이라 서비스라기보다 어댑터에 가깝다. 미디어 도메인이 챌린지 리포지터리를 직접
 * 알지 않도록 방향을 뒤집는 것이 목적이다 — 의존은 챌린지 → 미디어 한 방향으로만 흐른다.
 */
@Component
@RequiredArgsConstructor
public class ChallengeMediaReferenceSource implements MediaReferenceSource {

    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final ChallengeRepository challengeRepository;

    @Override
    public Set<MediaPurpose> coveredPurposes() {
        return Set.of(MediaPurpose.CHALLENGE_VERIFICATION);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findReferencedKeys(Collection<String> candidateKeys) {
        if (candidateKeys.isEmpty()) {
            // 빈 컬렉션으로 IN 절을 만들면 DB에 따라 문법 오류가 난다. 질의 자체를 하지 않는다.
            return Set.of();
        }
        Set<String> referenced = new HashSet<>(challengeVerificationRepository.findImageUrlsIn(candidateKeys));
        referenced.addAll(challengeRepository.findImageUrlsIn(candidateKeys));
        return referenced;
    }
}
