package com.lirouti.domain.image.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이미지의 용도. S3 key의 최상위 경로가 되어 용도별로 객체를 분리한다.
 * 새 용도가 필요하면 여기에 상수를 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ImagePurpose {
    CHALLENGE_VERIFICATION("challenge-verifications"),
    PROFILE("profiles");

    private final String pathPrefix;
}
