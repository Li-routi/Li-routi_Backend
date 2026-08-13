package com.lirouti.domain.popup.service.query;

import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.popup.converter.PopupConverter;
import com.lirouti.domain.popup.dto.response.PopupResDTO;
import com.lirouti.domain.popup.repository.PendingPopupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupQueryService {

    private final PendingPopupRepository pendingPopupRepository;
    private final MediaService mediaService;

    /**
     * 아직 안 보여준 팝업. <b>비어 있는 것이 정상 상태다.</b>
     *
     * <p><b>아무것도 잠그지 않는다.</b> 두 기기가 동시에 조회하면 양쪽에 뜨는데 막지 않는다 —
     * 이 팝업은 반드시 보여야 하는 것이라 <b>덜 보이는 쪽이 더 나쁜 실패</b>다. 표시 권한을
     * 미리 잡는 방식(claim token·lease)을 쓰면 앱이 표시 직전에 죽었을 때 만료될 때까지 어느
     * 기기에도 안 뜨는 구간이 생기고, 그 시간을 얼마로 할지 정할 근거도 없다.
     *
     * <p>여러 건이 밀려 있으면 <b>발생 순서대로</b> 전부 준다.
     */
    @Transactional(readOnly = true)
    public PopupResDTO.Popups getPending(Long memberId) {
        return PopupConverter.toPopups(
                pendingPopupRepository.findAllByMemberIdAndAckedAtIsNullOrderByCreatedAtAscIdAsc(memberId),
                // 팝업 이미지는 공개 자산의 key 만 담는다. 서명 주소를 담으면 앱을 늦게 켠
                // 사용자에게는 이미 만료된 주소가 간다 -- 팝업은 며칠 뒤에 열릴 수도 있다.
                mediaService::resolvePublicUrl
        );
    }
}
