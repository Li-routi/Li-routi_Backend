package com.lirouti.domain.popup.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class PopupReqDTO {

    private PopupReqDTO() {
    }

    @Schema(name = "AckPopups", description = "보여준 팝업 확인 요청")
    public record Ack(
            @NotEmpty(message = "확인할 팝업 id 는 하나 이상이어야 합니다.")
            @Schema(description = """
                    방금 보여준 팝업 id 목록. **한 번에 여러 건을 보여줬으면 함께 보낸다** —
                    건마다 따로 부르면 그 사이에 앱이 죽었을 때 일부만 확인된 상태가 남는다.""")
            List<Long> popupIds
    ) {
    }
}
