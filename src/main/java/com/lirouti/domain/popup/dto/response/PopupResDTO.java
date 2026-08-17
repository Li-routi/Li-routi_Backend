package com.lirouti.domain.popup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

public final class PopupResDTO {

    private PopupResDTO() {
    }

    @Schema(name = "PendingPopup", description = "아직 안 보여준 팝업 한 건")
    @Builder
    public record Popup(
            @Schema(description = "팝업 id. 확인(ack)할 때 그대로 돌려준다") Long id,
            @Schema(description = """
                    팝업 종류. `CHARACTER_UNLOCKED` 처럼 발행한 쪽이 정한다.
                    **모르는 값이 와도 그리기는 같다** — 종류로 분기하지 않아도 된다.""")
            String type,
            @Schema(description = "제목") String title,
            @Schema(description = "본문") String body,
            @Schema(description = "이미지 주소. 없을 수 있다") String imageUrl,
            @Schema(description = "눌렀을 때 보낼 곳의 종류. 없을 수 있다") String referenceType,
            @Schema(description = "그 대상의 id. 없을 수 있다") Long referenceId
    ) {
    }

    @Schema(name = "PendingPopups", description = "아직 안 보여준 팝업 목록")
    @Builder
    public record Popups(
            @Schema(description = "오래된 것부터. 비어 있는 것이 정상 상태다") List<Popup> popups
    ) {
    }
}
