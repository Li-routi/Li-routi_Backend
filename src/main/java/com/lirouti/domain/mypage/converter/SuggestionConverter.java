package com.lirouti.domain.mypage.converter;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.mypage.dto.response.SuggestionResDTO;
import com.lirouti.domain.mypage.entity.Suggestion;
import com.lirouti.domain.mypage.entity.SuggestionCategory;

import java.util.List;

public final class SuggestionConverter {

    private SuggestionConverter() {
    }

    public static Suggestion toEntity(Member member, SuggestionCategory category, String content) {
        return Suggestion.builder()
                .member(member)
                .category(category)
                .content(content)
                .build();
    }

    public static SuggestionResDTO.Category toCategory(SuggestionCategory category) {
        return SuggestionResDTO.Category.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .build();
    }

    public static SuggestionResDTO.Categories toCategories(List<SuggestionCategory> categories) {
        return SuggestionResDTO.Categories.builder()
                .categories(categories.stream().map(SuggestionConverter::toCategory).toList())
                .build();
    }

    public static SuggestionResDTO.Suggestion toSuggestion(Suggestion suggestion) {
        return SuggestionResDTO.Suggestion.builder()
                .id(suggestion.getId())
                .category(toCategory(suggestion.getCategory()))
                .content(suggestion.getContent())
                .status(suggestion.getStatus())
                .createdAt(suggestion.getCreatedAt())
                .build();
    }

    /**
     * 목록 응답.
     *
     * <p>다음 쪽이 있는지는 <b>부르는 쪽이 판단해서 넘긴다</b> — 그것은 한 건 더 읽어 보고 아는
     * 것이라 변환의 일이 아니다.
     */
    public static SuggestionResDTO.Listing toListing(List<Suggestion> suggestions, boolean hasNext) {
        List<SuggestionResDTO.Suggestion> items =
                suggestions.stream().map(SuggestionConverter::toSuggestion).toList();
        return SuggestionResDTO.Listing.builder()
                .suggestions(items)
                .hasNext(hasNext)
                .nextCursor(hasNext ? items.get(items.size() - 1).id() : null)
                .build();
    }
}
