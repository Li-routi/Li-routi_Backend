package com.lirouti.domain.popup.converter;

import com.lirouti.domain.popup.dto.response.PopupResDTO;
import com.lirouti.domain.popup.entity.PendingPopup;

import java.util.List;
import java.util.function.UnaryOperator;

public final class PopupConverter {

    private PopupConverter() {
    }

    /**
     * @param toViewUrl 저장된 S3 key 를 볼 수 있는 주소로 바꾼다. 컨버터가 미디어 설정을 알
     *                  필요가 없도록 호출하는 쪽에서 넣는다
     */
    public static PopupResDTO.Popup toPopup(PendingPopup popup, UnaryOperator<String> toViewUrl) {
        return PopupResDTO.Popup.builder()
                .id(popup.getId())
                .type(popup.getPopupType())
                .title(popup.getTitle())
                .body(popup.getBody())
                // 이미지 없는 팝업이 있을 수 있다. 없는 것을 조립하면 오리진만 남은 주소가 나간다.
                .imageUrl(popup.getImageKey() == null ? null : toViewUrl.apply(popup.getImageKey()))
                .referenceType(popup.getReferenceType())
                .referenceId(popup.getReferenceId())
                .build();
    }

    public static PopupResDTO.Popups toPopups(List<PendingPopup> popups,
                                              UnaryOperator<String> toViewUrl) {
        return PopupResDTO.Popups.builder()
                .popups(popups.stream().map(popup -> toPopup(popup, toViewUrl)).toList())
                .build();
    }
}
