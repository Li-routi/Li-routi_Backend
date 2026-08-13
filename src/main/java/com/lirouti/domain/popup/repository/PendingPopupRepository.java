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
     */
    List<PendingPopup> findAllByMemberIdAndAckedAtIsNullOrderByCreatedAtAsc(Long memberId);

    /** 남의 팝업을 확인 처리할 수 없도록 회원까지 함께 건다. */
    Optional<PendingPopup> findByIdAndMemberId(Long id, Long memberId);

    /** 발행 전 선검사. 최종 판정은 uk_pending_popup_member_dedup 이 한다. */
    boolean existsByMemberIdAndDedupKey(Long memberId, String dedupKey);
}
