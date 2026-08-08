package com.lirouti.domain.media.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 미디어의 용도. S3 key의 최상위 경로가 되어 용도별로 객체를 분리하고,
 * 어떤 카테고리(사진/영상)를 허용하는지도 용도별로 정한다.
 *
 * <b>공개 여부가 용도마다 다르다.</b> 그 갈림을 여기 {@code publicRead} 가 들고 있고,
 * 읽기 URL 조립이 그 값을 보고 갈린다(MediaService#resolveViewUrl).
 *
 * <p><b>이 값은 버킷 정책과 짝이다.</b> 둘이 어긋나면 조용히 깨진다 — 공개로 표시했는데
 * 정책이 안 열려 있으면 조회가 403 이고, 비공개로 표시했는데 정책이 열려 있으면 서명 없이도
 * 열려 서명이 무의미해진다. 값을 바꾸려면 정책을 먼저 확인한다.
 *
 * <p>지금 버킷 정책이 여는 것은 challenge-verifications/ <b>하나뿐이다</b>
 * (deploy/bucket-policy-media.json).
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
    CHALLENGE_VERIFICATION("challenge-verifications", Set.of(MediaCategory.IMAGE), true, true),
    // 그 방 멤버 전원만, 본인만 볼 수 있다. 버킷 정책이 열려 있지 않아 서명으로만 열린다.
    GROUP_ROUTINE_VERIFICATION("group-routine-verifications", Set.of(MediaCategory.IMAGE), false, true),
    MEMBER_ROUTINE_VERIFICATION("member-routine-verifications", Set.of(MediaCategory.IMAGE), false, true),
    // 공개 용도로 설계돼 있지만 정책에는 아직 없다 — 프로필 이미지 자체가 없어 미리 열면
    // "언제 왜 열었는지 모르는 공개 prefix"가 남는다는 판단이었다(deploy/README.md).
    // 정책에 없는 것을 공개로 표시하면 서명 없는 주소가 나가 403 이 되므로 지금은 비공개다.
    // 프로필 업로드를 구현하며 정책을 열 때 이 값을 함께 true 로 바꾼다.
    PROFILE("profiles", Set.of(MediaCategory.IMAGE), false, true),
    // 서비스가 등록한 자산만 사용하며 일반 사용자에게 presigned PUT을 발급하지 않는다.
    CHAT_EMOTICON("chat-emoticons", Set.of(MediaCategory.IMAGE), false, false);

    private final String pathPrefix;
    private final Set<MediaCategory> allowedCategories;

    /** 익명 읽기가 열려 있는 prefix 인가. 버킷 정책과 반드시 일치해야 한다. */
    private final boolean publicRead;

    /** 일반 사용자에게 presigned PUT을 발급할 수 있는 용도인가. */
    private final boolean clientUploadAllowed;

    public boolean allows(MediaCategory category) {
        return allowedCategories.contains(category);
    }
}
