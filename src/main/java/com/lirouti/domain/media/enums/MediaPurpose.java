package com.lirouti.domain.media.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 미디어의 용도. S3 key의 최상위 경로가 되어 용도별로 객체를 분리하고,
 * 어떤 카테고리(사진/영상)를 허용하는지도 용도별로 정한다.
 *
 * <b>공개 여부가 용도마다 다르다.</b> 챌린지 인증만 공개 prefix 이고 나머지는 비공개다.
 * 버킷 정책이 challenge-verifications/ 하나만 익명 읽기로 열려 있어, 다른 prefix 의 사진은
 * 서명된 URL 로만 볼 수 있다. 그 발급은 아직 구현돼 있지 않다 — 지금은 저장까지만 된다.
 *
 * 현재 모든 용도는 사진(IMAGE)만 허용한다. 특정 용도에서 영상을 받으려면
 * 해당 용도의 allowedCategories에 MediaCategory.VIDEO를 추가한다.
 * 예) 챌린지 인증에 영상을 허용하려면 Set.of(IMAGE, VIDEO)로 바꾼다.
 */
@Getter
@RequiredArgsConstructor
public enum MediaPurpose {
    // TODO(영상): 특정 용도에 영상을 허용하려면 그 용도의 Set에 MediaCategory.VIDEO 추가
    //  예) CHALLENGE_VERIFICATION("challenge-verifications", Set.of(MediaCategory.IMAGE, MediaCategory.VIDEO))
    CHALLENGE_VERIFICATION("challenge-verifications", Set.of(MediaCategory.IMAGE)),
    GROUP_ROUTINE_VERIFICATION("group-routine-verifications", Set.of(MediaCategory.IMAGE)),
    MEMBER_ROUTINE_VERIFICATION("member-routine-verifications", Set.of(MediaCategory.IMAGE)),
    PROFILE("profiles", Set.of(MediaCategory.IMAGE));

    private final String pathPrefix;
    private final Set<MediaCategory> allowedCategories;

    public boolean allows(MediaCategory category) {
        return allowedCategories.contains(category);
    }
}
