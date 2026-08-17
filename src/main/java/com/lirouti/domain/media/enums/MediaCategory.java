package com.lirouti.domain.media.enums;

/**
 * 미디어의 큰 갈래. 용량 한도·허용 용도를 이 단위로 나눈다.
 * 현재는 사진(IMAGE)만 실제로 사용한다. 영상(VIDEO)은 확장 지점으로 미리 정의만 해두었으며,
 * {@link MediaContentType}에 영상 형식을 추가하고 {@link MediaPurpose}의 허용 목록에
 * VIDEO를 넣기 전까지는 어떤 요청도 VIDEO로 분류되지 않는다.
 */
public enum MediaCategory {
    IMAGE,
    VIDEO
}
