package com.lirouti.domain.popup.repository;

import com.lirouti.domain.popup.entity.PendingPopup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendingPopupRepository extends JpaRepository<PendingPopup, Long> {

    /**
     * 아직 안 보여준 것만, 오래된 것부터.
     *
     * <p>여러 건이 한꺼번에 밀려 있을 수 있다 — 며칠 만에 켰는데 그 사이 둘이 열렸다면 둘 다
     * 보여줘야 한다. 그래서 하나가 아니라 목록이고, 발생 순서대로 준다.
     *
     * <p><b>{@code id} 로 동점을 가른다.</b> 한 트랜잭션에서 둘을 발행하면 {@code created_at}
     * 이 같은 값일 수 있고, 그러면 순서가 조회마다 달라진다 — 앱이 팝업을 띄우는 순서가
     * 흔들린다는 뜻이다. 보조 인덱스는 기본 키를 이미 품고 있어 정렬이 인덱스를 벗어나지 않는다.
     */
    List<PendingPopup> findAllByMemberIdAndAckedAtIsNullOrderByCreatedAtAscIdAsc(Long memberId);

    /**
     * 확인할 것들을 한 번에 읽는다. <b>회원을 함께 걸어 남의 팝업이 섞이지 않게 한다.</b>
     *
     * <p>건마다 따로 읽으면 목록 길이만큼 쿼리가 나간다. 한 번에 여러 건을 확인하는 것이
     * 정상 경로라(밀린 팝업을 한꺼번에 보여준다) 그 모양대로 읽는다.
     */
    List<PendingPopup> findAllByIdInAndMemberId(List<Long> ids, Long memberId);

    /** 발행 전 선검사. 최종 판정은 uk_pending_popup_member_dedup 이 한다. */
    boolean existsByMemberIdAndDedupKey(Long memberId, String dedupKey);
}
