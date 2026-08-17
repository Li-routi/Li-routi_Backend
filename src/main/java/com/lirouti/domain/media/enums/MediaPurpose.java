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
 * <p>지금 버킷 정책이 여는 것은 challenge-verifications/ 와 avatar/ <b>둘뿐이다</b>
 * (deploy/bucket-policy-media.json). 성격이 다르다 — 앞은 사용자가 올린 사진이라 key 가
 * UUID 여서 추측되지 않는 것에 기대고, 뒤는 운영이 올린 마스터 자산이라 모두에게 같은
 * 그림이므로 숨길 것이 없다.
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
    // 인증 사진 셋은 정책 하나를 나눠 쓴다. 한 사람이 아침에 개인 루틴을 인증하고 그룹 인증도
    // 하는 것이 정상 사용이라 서로 굶길 이유가 없다. 성격이 다른 것은 프로필이다 — 자주 바꾸지
    // 않고, 바꿀 때 몰아서 바꾼다. 그래서 프로필만 따로 뗀다.
    CHALLENGE_VERIFICATION(
            "challenge-verifications", "challenge-verifications-staging",
            Set.of(MediaCategory.IMAGE), true, true, "media-presign-verification"),
    // 그 방 멤버 전원만, 본인만 볼 수 있다. 버킷 정책이 열려 있지 않아 서명으로만 열린다.
    GROUP_ROUTINE_VERIFICATION("group-routine-verifications", null,
            Set.of(MediaCategory.IMAGE), false, true, "media-presign-verification"),
    MEMBER_ROUTINE_VERIFICATION("member-routine-verifications", null,
            Set.of(MediaCategory.IMAGE), false, true, "media-presign-verification"),
    // 공개 용도로 설계돼 있지만 정책에는 아직 없다 — 프로필 이미지 자체가 없어 미리 열면
    // "언제 왜 열었는지 모르는 공개 prefix"가 남는다는 판단이었다(deploy/README.md).
    // 정책에 없는 것을 공개로 표시하면 서명 없는 주소가 나가 403 이 되므로 지금은 비공개다.
    // 프로필 업로드를 구현하며 정책을 열 때 이 값을 함께 true 로 바꾼다.
    PROFILE("profiles", null, Set.of(MediaCategory.IMAGE), false, true, "media-presign-profile"),
    // 서비스가 등록한 자산만 사용하며 일반 사용자에게 presigned PUT을 발급하지 않는다.
    // 발급 경로가 없으니 셀 것도 없어 정책이 null 이다.
    CHAT_EMOTICON("chat-emoticons", null, Set.of(MediaCategory.IMAGE), false, false, null),
    // 업적별 현재 뱃지 하나를 서버가 관리한다. 읽기는 공개하고 업로드는 서버만 수행한다.
    ACHIEVEMENT_BADGE("achievement-badges", null, Set.of(MediaCategory.IMAGE), true, false, null),
    // 캐릭터·알·둥지·의상. 운영이 직접 올리는 마스터 자산이라 이모티콘과 같은 취급이다 —
    // 발급 경로가 없어 셀 것도 없다. 다른 점은 공개라는 것뿐이다. 모두에게 같은 그림이라
    // 숨길 것이 없고, 서명 주소로 내리면 만료마다 다시 발급해야 해서 얻는 것 없이 비싸진다.
    //
    // 최상위 prefix 를 하나로 모아 버킷 정책이 avatar/* 한 줄로 끝난다. 자산 종류가 늘어도
    // 정책을 다시 고치지 않는다.
    //
    // ⚠️ 정리 배치의 대상이 아니다. 참조 원천이 R__ 시드라 MediaReferenceSource 가 없고,
    //    key 에 날짜 구간도 없다. 훑게 두면 전부 미참조로 보여 지워진다.
    AVATAR_ASSET("avatar", null, Set.of(MediaCategory.IMAGE), true, false, null);

    private final String pathPrefix;

    /**
     * 심사를 통과하기 전 사진을 받아 두는 비공개 prefix. 해당 없으면 {@code null}.
     *
     * <p>챌린지 인증만 값을 갖는다. 사진이 AI 심사를 거치는 유일한 용도이면서 통과 후에는
     * 전체 공개이기 때문이다. 심사 전부터 공개 주소가 살아 있으면 <b>반려돼도 그 사이 열람하거나
     * 공유한 것은 회수되지 않는다.</b>
     *
     * <p><b>별도 최상위 prefix 여야 한다.</b> 버킷 정책이 {@code challenge-verifications/*} 를
     * 여는데, 그 아래({@code challenge-verifications/staging/…})에 두면 와일드카드에 걸려
     * 그대로 공개된다. 접두사 뒤의 {@code /} 때문에 지금 이름은 걸리지 않는다.
     */
    private final String stagingPrefix;

    private final Set<MediaCategory> allowedCategories;

    /** 익명 읽기가 열려 있는 prefix 인가. 버킷 정책과 반드시 일치해야 한다. */
    private final boolean publicRead;

    /** 일반 사용자에게 presigned PUT을 발급할 수 있는 용도인가. */
    private final boolean clientUploadAllowed;

    /**
     * 발급 빈도를 셀 때 쓸 {@code rate-limit.policies} 키. 셀 필요가 없으면 {@code null}.
     *
     * <p><b>여기 있는 것은 정책 "이름"뿐이고 한도 값은 설정에 있다.</b> 컨트롤러에
     * {@code @RateLimit("정책이름")}만 적고 횟수·창은 yaml 에 두는 것과 같은 분리다 —
     * 운영에서 값을 조정할 때 코드를 고치지 않기 위해서다.
     *
     * <p>용도별 갈림을 이 enum 이 들고 있는 이유는 prefix·공개 여부·허용 카테고리처럼
     * <b>이미 용도마다 다른 값들이 여기 모여 있기</b> 때문이다. 매핑을 설정으로 빼면 용도를
     * 추가할 때 두 곳을 고쳐야 하고, 한쪽을 빠뜨려도 부팅은 성공한다.
     */
    private final String rateLimitPolicy;

    public boolean allows(MediaCategory category) {
        return allowedCategories.contains(category);
    }

    /** 심사를 거쳐 승격되는 용도인가. */
    public boolean hasStaging() {
        return stagingPrefix != null;
    }

    /**
     * 업로드를 받을 prefix. 대기 prefix 가 있으면 그쪽으로 받는다.
     *
     * <p>클라이언트는 이 갈림을 모른다 — 발급 요청에 보내는 용도는 그대로 챌린지 인증이고,
     * 어디로 받을지는 서버가 정한다.
     */
    public String getUploadPrefix() {
        return hasStaging() ? stagingPrefix : pathPrefix;
    }
}
