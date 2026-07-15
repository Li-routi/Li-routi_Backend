package com.lirouti.domain.media.enums;

import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 미디어의 용도. S3 key의 최상위 경로가 되어 용도별로 객체를 분리하고,
 * 어떤 카테고리(사진/영상)를 허용하는지도 용도별로 정한다.
 *
 * <p>현재 모든 용도는 사진(IMAGE)만 허용한다. 특정 용도에서 영상을 받으려면
 * 해당 용도의 {@code allowedCategories}에 {@link MediaCategory#VIDEO}를 추가한다.
 * 예) 챌린지 인증에 영상을 허용하려면 {@code Set.of(IMAGE, VIDEO)}로 바꾼다.
 */
@Getter
@RequiredArgsConstructor
public enum MediaPurpose {
    CHALLENGE_VERIFICATION("challenge-verifications", Set.of(MediaCategory.IMAGE)),
    PROFILE("profiles", Set.of(MediaCategory.IMAGE));

    private final String pathPrefix;
    private final Set<MediaCategory> allowedCategories;

    public boolean allows(MediaCategory category) {
        return allowedCategories.contains(category);
    }
}
