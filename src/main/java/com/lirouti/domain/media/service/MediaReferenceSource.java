package com.lirouti.domain.media.service;

import com.lirouti.domain.media.enums.MediaPurpose;

import java.util.Collection;
import java.util.Set;

/**
 * 어떤 미디어 key가 <b>아직 쓰이고 있는지</b>를 답하는 쪽. 미참조 이미지 정리가 이걸 보고
 * 지울 대상을 정한다.
 *
 * <p><b>이 인터페이스가 곧 정리 대상의 화이트리스트다.</b> 정리는 어떤 구현체도 담당하지 않는
 * 용도(purpose)는 <b>절대 건드리지 않는다.</b> 담당자가 없다는 건 "그 prefix에 무엇이 참조되고
 * 있는지 아무도 모른다"는 뜻이고, 그 상태에서 지우면 살아 있는 파일을 지운다.
 *
 * <p>실제로 지금 {@link MediaPurpose#PROFILE}이 그런 상태다 — enum에는 있지만 발급된 적이 없고
 * 그 key를 담는 컬럼도 없다. 나중에 프로필 업로드를 붙이는 사람이 참조처를 등록하지 않으면,
 * 화이트리스트가 없을 때 정리 배치가 사용자 프로필 사진을 조용히 지워버린다.
 * 그래서 "빠뜨리면 안 지운다"가 되도록 구조로 막았다. 주석으로 막으면 언젠가 뚫린다.
 *
 * <p><b>구현할 때 지킬 것</b>
 * <ul>
 *   <li>그 용도의 key를 담는 <b>모든</b> 컬럼을 봐야 한다. 하나라도 빠뜨리면 그 컬럼이 가리키는
 *       파일이 지워진다.</li>
 *   <li>탈퇴·신고로 숨겨진 행도 <b>참조로 친다.</b> 행이 남아 있으면 key도 살아 있는 것이다.
 *       탈퇴 회원의 사진을 지우는 건 별개 정책이다.</li>
 *   <li>조회 전용이므로 {@code @Transactional(readOnly = true)}로 둔다.</li>
 * </ul>
 */
public interface MediaReferenceSource {

    /** 이 구현체가 참조 여부를 책임지는 용도. 여기 없는 용도는 정리 대상에서 빠진다. */
    Set<MediaPurpose> coveredPurposes();

    /**
     * 후보 key 중 <b>실제로 참조되고 있는</b> 것만 골라 돌려준다.
     *
     * <p>반환되지 않은 key는 지워진다. 확신이 없으면 넣는 쪽이 안전하다 —
     * 잘못 넣으면 파일이 하루 더 남을 뿐이고, 잘못 빼면 사용자 사진이 사라진다.
     *
     * @param candidateKeys S3에서 훑어온 key. 비어 있을 수 있다.
     * @return 그중 참조 중인 key. 후보에 없던 key를 넣어도 무시된다.
     */
    Set<String> findReferencedKeys(Collection<String> candidateKeys);
}
