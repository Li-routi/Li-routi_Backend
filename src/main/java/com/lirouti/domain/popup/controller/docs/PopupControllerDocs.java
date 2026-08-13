package com.lirouti.domain.popup.controller.docs;

import com.lirouti.domain.popup.dto.request.PopupReqDTO;
import com.lirouti.domain.popup.dto.response.PopupResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Popup", description = "앱 진입 시 띄울 팝업 API")
public interface PopupControllerDocs {

    @Operation(
            summary = "안 보여준 팝업 조회",
            description = """
                    **앱을 켤 때 한 번 부른다.** 아직 안 보여준 것만 오래된 것부터 내려간다.
                    비어 있는 것이 정상 상태다.

                    **여러 건이 올 수 있다.** 며칠 만에 켰는데 그 사이 둘이 열렸다면 둘 다
                    보여줘야 한다 — 받은 순서대로 띄우면 된다.

                    **`type` 으로 분기하지 않아도 된다.** 제목·본문·이미지가 이미 채워져
                    있어서 그리는 방법은 종류와 무관하다. 모르는 값이 와도 그대로 그린다 —
                    새 종류가 생겨도 앱을 고치지 않게 하려는 계약이다.

                    `referenceType`·`referenceId` 는 눌렀을 때 보낼 곳이다. 없으면 누를 곳도
                    없다는 뜻이다.

                    > **본 뒤에는 반드시 확인(ack)을 보내야 한다.** 안 보내면 다음에 또 뜬다.
                    > 두 기기에서 동시에 켜면 양쪽에 뜰 수 있는데, **덜 보이는 것보다 두 번
                    > 보이는 편이 낫다**고 보고 막지 않았다.
                    """
    )
    ApiResponse<PopupResDTO.Popups> getPending(CustomUserDetails userDetails);

    @Operation(
            summary = "팝업 확인",
            description = """
                    방금 보여준 팝업을 확인 처리한다. **한 번에 여러 건을 보여줬으면 함께
                    보낸다** — 건마다 따로 부르면 그 사이 앱이 죽었을 때 일부만 확인된 상태가
                    남는다.

                    **멱등하다.** 이미 확인한 것을 다시 보내도 성공이고, 처음 본 시각도 그대로
                    둔다. 앱이 재시도하는 것이 정상 경로다.

                    **하나라도 남의 것이거나 없는 id 면 전체를 거절한다.** 부분 성공을 허용하면
                    앱은 무엇이 확인됐는지 모른 채 다음 조회에서 일부만 다시 받는다.

                    - `POPUP404_1` 없는 팝업이거나 남의 팝업
                    """
    )
    ApiResponse<Void> ack(PopupReqDTO.Ack request, CustomUserDetails userDetails);
}
