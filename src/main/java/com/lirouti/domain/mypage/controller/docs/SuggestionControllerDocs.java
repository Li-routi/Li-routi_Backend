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
                    분류와 제목, 본문을 보낸다. 제목은 **100자**, 본문은 **2000자**까지다.

                    제목은 앞뒤 공백을 떼고 저장된다. 공백만 보내면 빈 제목으로 보고 거절한다 —
                    목록에 빈 줄이 뜨는 것을 막는다.

                    등록된 건의는 **수정·삭제할 수 없다.** 운영이 읽은 뒤에 내용이 바뀌면 무엇을
                    보고 처리했는지 알 수 없기 때문이다.

                    - `SUGGESTION400_2` 본문이 비었거나 2000자 초과
                    - `SUGGESTION400_3` 제목이 비었거나 100자 초과
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

                    ### 검색

                    `keyword` 는 **제목**, `categoryId` 는 **분류**로 거른다. 둘 다 주면 **둘 다
                    만족하는 것만** 나온다. 둘 다 비우면 전체다.

                    `keyword` 는 부분 일치이고 대소문자를 구별하지 않는다.
                    본문은 검색하지 않는다 — 긴 글 안의 아무 단어나 걸리면 목록이 뭉개진다.

                    `%` 나 `_` 를 넣어도 와일드카드로 동작하지 않는다. 제목에 그 글자가 들어간
                    건의를 찾는다.

                    비우거나 공백만 보내면 **검색하지 않은 것과 같다** — 전체가 나온다.

                    `categoryId` 는 **내려간 분류도 받는다.** 그 분류로 이미 보낸 건의를 찾을 수
                    있어야 하기 때문이다 — 고를 수 없게 하는 것과 찾을 수 없게 하는 것은 다르다.
                    다만 **없는 분류 id 는 거절한다**(`SUGGESTION404_1`). 조용히 빈 목록을 주면
                    "이 분류에는 건의가 없다" 로 읽혀, 잘못된 id 를 보내고 있다는 것을 모른다.

                    걸러도 커서 방식은 그대로다. **같은 조건을 유지한 채** `nextCursor` 를 넘겨야
                    다음 쪽이 이어진다. 조건을 바꾸면 커서를 버리고 처음부터 받는다.
                    """
    )
    ApiResponse<SuggestionResDTO.Listing> getMySuggestions(
            @Parameter(description = "이전 응답의 nextCursor. 첫 요청이면 비운다") Long cursor,
            @Parameter(description = "한 번에 받을 개수. 기본 20, 1~50") Integer size,
            @Parameter(description = "제목 검색어. 부분 일치. 비우면 전체")
            String keyword,
            @Parameter(description = "분류 id 로 거른다. 비우면 전체. 내려간 분류도 받는다")
            Long categoryId,
            CustomUserDetails userDetails);
}
