package com.lirouti.domain.mypage.controller.docs;

import com.lirouti.domain.mypage.dto.request.SuggestionReqDTO;
import com.lirouti.domain.mypage.dto.response.SuggestionResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "MyPage - 건의", description = "마이페이지 건의 API")
public interface SuggestionControllerDocs {

    @Operation(
            summary = "건의 분류 목록",
            description = """
                    건의를 등록할 때 고르는 분류다. **노출 순서대로 이미 정렬돼 있다.**

                    **내려간 분류는 실리지 않는다.** 여기 없는 id 로 등록하면 거절된다.

                    분류는 서버가 관리하는 마스터 데이터라 앱에 상수로 박지 않는다 — 늘어나거나
                    이름이 바뀌어도 앱 배포가 필요 없어야 한다.
                    """
    )
    ApiResponse<SuggestionResDTO.Categories> getCategories();

    @Operation(
            summary = "건의 등록",
            description = """
                    분류와 본문을 보낸다. **제목은 받지 않는다** — 분류가 그 자리를 대신한다.

                    본문은 2000자까지다.

                    등록된 건의는 **수정·삭제할 수 없다.** 운영이 읽은 뒤에 내용이 바뀌면 무엇을
                    보고 처리했는지 알 수 없기 때문이다.

                    - `SUGGESTION404_1` 없는 분류
                    - `SUGGESTION409_1` 내려간 분류 → 목록을 다시 받아 고른다
                    """
    )
    ApiResponse<SuggestionResDTO.Suggestion> create(SuggestionReqDTO.Create request,
                                                    CustomUserDetails userDetails);

    @Operation(
            summary = "내 건의 목록",
            description = """
                    **내가 보낸 것만 나온다.** 남의 건의는 보이지 않는다.

                    최신순 커서 페이지네이션이다. 첫 요청은 `cursor` 없이 보내고, 응답의
                    `nextCursor` 를 다음 요청의 `cursor` 로 넘긴다. `hasNext` 가 `false`
                    (= `nextCursor` 가 `null`)면 더 이상 요청하지 않는다.

                    `size` 는 기본 20, **1~50** 이다. 벗어나면 400 이다 — 조용히 상한으로
                    깎지 않는다. 그러면 요청한 수와 받은 수가 다른 이유를 알 수 없다.

                    **분류가 내려갔어도 그대로 나간다.** 고를 수 없게 하는 것과 이미 보낸 것을
                    감추는 것은 다르다.

                    상태는 지금 **전부 `RECEIVED`** 다. 바꾸는 관리자 화면이 아직 없다.
                    """
    )
    ApiResponse<SuggestionResDTO.Listing> getMySuggestions(
            @Parameter(description = "이전 응답의 nextCursor. 첫 요청이면 비운다") Long cursor,
            @Parameter(description = "한 번에 받을 개수. 기본 20, 1~50") Integer size,
            CustomUserDetails userDetails);
}
