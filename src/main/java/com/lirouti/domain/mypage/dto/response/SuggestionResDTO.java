package com.lirouti.domain.mypage.dto.response;

import com.lirouti.domain.mypage.enums.SuggestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public final class SuggestionResDTO {

    private SuggestionResDTO() {
    }

    @Schema(name = "SuggestionCategory", description = "건의 분류 하나")
    @Builder
    public record Category(
            @Schema(description = "분류 id. 등록할 때 이 값을 보낸다") Long id,
            @Schema(description = "논리 키", example = "MAIN") String code,
            @Schema(description = "화면에 쓸 이름", example = "메인") String name
    ) {
    }

    @Schema(name = "SuggestionCategories", description = "건의 분류 목록")
    @Builder
    public record Categories(
            @Schema(description = "노출 순서대로 이미 정렬돼 있다") List<Category> categories
    ) {
    }

    @Schema(name = "Suggestion", description = "내가 보낸 건의 한 건")
    @Builder
    public record Suggestion(
            @Schema(description = "건의 id") Long id,
            @Schema(description = """
                    보낼 때 고른 분류. **내려간 분류여도 그대로 나간다** — 분류가 내려갔다고
                    이미 보낸 건의의 분류명이 사라지면 안 된다.""")
            Category category,
            @Schema(description = "본문") String content,
            @Schema(description = """
                    처리 상태. **지금은 전부 `RECEIVED` 다** — 상태를 바꾸는 관리자 화면이
                    아직 없다.""")
            SuggestionStatus status,
            @Schema(description = "보낸 시각") LocalDateTime createdAt
    ) {
    }

    /**
     * 내 건의 목록.
     *
     * <p>클라이언트는 첫 요청에 {@code cursor} 없이 보내고, 응답의 {@code nextCursor} 를 다음
     * 요청의 {@code cursor} 로 넘긴다. {@code hasNext} 가 {@code false}(= {@code nextCursor} 가
     * {@code null})면 더 이상 요청하지 않는다.
     */
    @Schema(name = "SuggestionListing", description = "내 건의 목록")
    @Builder
    public record Listing(
            @Schema(description = "최신순") List<Suggestion> suggestions,
            @Schema(description = "다음 요청에 넘길 커서. 없으면 마지막 쪽이다") Long nextCursor,
            @Schema(description = "다음 쪽이 있는가") boolean hasNext
    ) {
    }
}
