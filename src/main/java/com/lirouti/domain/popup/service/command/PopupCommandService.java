package com.lirouti.domain.popup.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.popup.entity.PendingPopup;
import com.lirouti.domain.popup.exception.PopupException;
import com.lirouti.domain.popup.exception.code.error.PopupErrorCode;
import com.lirouti.domain.popup.repository.PendingPopupRepository;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopupCommandService {

    private final PendingPopupRepository pendingPopupRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    /**
     * 팝업을 발행한다. <b>다른 도메인이 부르는 입구다.</b>
     *
     * <p><b>같은 트랜잭션 안에서 불러야 한다.</b> 사건(해금·업적 달성)과 나누면 사건은
     * 일어났는데 팝업이 없는 상태가 남고, 그 사용자는 무엇이 열렸는지 영영 모른다.
     *
     * <p><b>이미 발행된 사건이면 아무 일도 하지 않는다.</b> 판정이 여러 번 도는 것이 정상이라
     * 오류로 만들지 않는다. 최종 판정은 {@code uk_pending_popup_member_dedup} 이 하고, 아래
     * 검사는 선검사일 뿐이다 — 동시에 둘이 통과하면 유니크가 뒤엣것을 튕기고 그 트랜잭션이
     * 되돌아간다.
     */
    @Transactional
    public void publish(PopupPublishCommand command) {
        if (pendingPopupRepository.existsByMemberIdAndDedupKey(
                command.memberId(), command.dedupKey())) {
            return;
        }

        Member member = memberRepository.findById(command.memberId())
                .orElseThrow(() -> new GeneralException(MemberErrorCode.MEMBER_NOT_FOUND));

        pendingPopupRepository.save(PendingPopup.builder()
                .member(member)
                .popupType(command.popupType())
                .title(command.title())
                .body(command.body())
                .imageKey(command.imageKey())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .dedupKey(command.dedupKey())
                .build());
    }

    /**
     * 보여준 팝업을 확인 처리한다.
     *
     * <p><b>멱등하다.</b> 이미 확인한 것을 다시 보내도 성공이고 처음 본 시각도 그대로 둔다 —
     * 앱이 재시도하는 것이 정상 경로다.
     *
     * <p><b>남의 팝업은 확인할 수 없다.</b> 없는 id 와 남의 id 를 가르지 않고 똑같이 404 로
     * 답한다 — 가르면 id 를 넣어 보는 것만으로 남의 팝업이 존재하는지 알 수 있다.
     *
     * <p><b>하나라도 남의 것이면 전체를 거절한다.</b> 부분 성공을 허용하면 앱은 무엇이
     * 확인됐는지 모른 채 다음 조회에서 일부만 다시 받게 된다.
     */
    @Transactional
    public void ack(Long memberId, List<Long> popupIds) {
        List<Long> distinctIds = popupIds.stream().distinct().toList();
        List<PendingPopup> popups =
                pendingPopupRepository.findAllByIdInAndMemberId(distinctIds, memberId);

        // 하나라도 못 찾았으면 남의 것이거나 없는 것이다. 무엇이 빠졌는지는 알려주지 않는다.
        if (popups.size() != distinctIds.size()) {
            throw new PopupException(PopupErrorCode.POPUP_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        popups.forEach(popup -> popup.ack(now));
    }
}
