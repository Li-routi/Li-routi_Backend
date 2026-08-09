package com.lirouti.domain.verification.service;

import com.lirouti.domain.challenge.repository.ChallengeRepository;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaReferenceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 챌린지와 그 인증이 쓰고 있는 미디어 key를 미디어 정리 배치에 알려준다.
 *
 * <p>정리 대상의 대부분이 인증 사진이라 이 클래스는 {@code verification} 에 둔다. 다만 챌린지
 * 대표 이미지도 같은 버킷을 쓰게 될 수 있어 함께 본다 — 두 테이블에 걸치는 것이 이 클래스의
 * 성격이다.
 *
 * <p><b>여기 빠뜨린 컬럼이 있으면 그 컬럼이 가리키는 파일이 지워진다.</b> 챌린지나 인증 쪽에
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
 * <h3>다만 대표 이미지가 보호되는 범위에는 전제가 있다</h3>
 * {@link #coveredPurposes()} 가 {@code CHALLENGE_VERIFICATION} 하나만 돌려주므로, 정리 배치가
 * 이 클래스에 넘겨 주는 후보는 <b>그 용도의 prefix 에 있는 key 뿐이다.</b> 위의
 * {@code challenge.image_url} 조회는 그 후보 안에서만 걸러 낸다.
 *
 * <p>따라서 대표 이미지가 <b>인증 사진과 같은 prefix</b> 를 쓸 때만 이 방어가 작동한다. 대표
 * 이미지를 인증 사진 중에서 고르는 방향이라면 key 가 그대로라 문제가 없다.
 *
 * <p>반대로 대표 이미지에 <b>별도 {@code MediaPurpose} 를 새로 만들면</b> 그 용도에는 참조처가
 * 없어 정리 배치가 그 prefix 를 <b>통째로 건너뛴다</b>(용도별로 묶어 돌기 때문이다). 지워지지는
 * 않으니 사고는 아니지만 미참조 파일이 영영 쌓인다. 그때는 이 클래스의
 * {@code coveredPurposes()} 에 새 용도를 더하는 것을 함께 해야 한다.
 *
 * <p><b>탈퇴·신고로 숨겨진 인증도 참조로 친다.</b> 행이 남아 있으면 key도 살아 있는 것이다.
 * 탈퇴 회원의 사진을 지우는 것은 이 배치가 아니라 별도 정책이 할 일이다.
 *
 * <p>조회 전용이라 서비스라기보다 어댑터에 가깝다. 미디어 도메인이 인증·챌린지 리포지터리를
 * 직접 알지 않도록 방향을 뒤집는 것이 목적이다 — 의존은 인증 → 미디어 한 방향으로만 흐른다.
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
