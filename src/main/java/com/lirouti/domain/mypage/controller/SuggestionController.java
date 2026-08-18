package com.lirouti.domain.mypage.controller;

import com.lirouti.domain.mypage.controller.docs.SuggestionControllerDocs;
import com.lirouti.domain.mypage.dto.request.SuggestionReqDTO;
import com.lirouti.domain.mypage.dto.response.SuggestionResDTO;
import com.lirouti.domain.mypage.exception.code.success.SuggestionSuccessCode;
import com.lirouti.domain.mypage.service.command.SuggestionCommandService;
import com.lirouti.domain.mypage.service.query.SuggestionQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 건의.
 *
 * <p><b>경로가 {@code /api/members/me} 아래인 것이 곧 격리 규칙이다.</b> 회원 식별자를 경로에서
 * 받지 않으므로 남의 것을 볼 방법이 없다 — {@code /api/members/{memberId}/suggestions} 로 두면
 * 그 값을 검증하는 코드가 매번 필요해진다.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/suggestions")
public class SuggestionController implements SuggestionControllerDocs {

    /** 한 번에 받을 수 있는 최대 개수. 상한이 없으면 자기 건의 전부를 한 번에 끌어갈 수 있다. */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SuggestionQueryService suggestionQueryService;
    private final SuggestionCommandService suggestionCommandService;

    @Override
    @GetMapping("/categories")
    public ApiResponse<SuggestionResDTO.Categories> getCategories() {
        return ApiResponse.onSuccess(
                SuggestionSuccessCode.SUGGESTION_CATEGORY_LIST_FETCH_SUCCESS,
                suggestionQueryService.getCategories());
    }

    @Override
    @PostMapping
    public ApiResponse<SuggestionResDTO.Suggestion> create(
            @Valid @RequestBody SuggestionReqDTO.Create request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        SuggestionResDTO.Suggestion result = suggestionCommandService.create(
                userDetails.getMemberId(), request.categoryId(), request.content());
        return ApiResponse.onSuccess(SuggestionSuccessCode.SUGGESTION_CREATE_SUCCESS, result);
    }

    @Override
    @GetMapping
    public ApiResponse<SuggestionResDTO.Listing> getMySuggestions(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false)
            @Min(value = 1, message = "size 는 1 이상이어야 합니다.")
            @Max(value = MAX_PAGE_SIZE, message = "size 는 50 이하여야 합니다.")
            Integer size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 회원 식별자를 요청에서 받지 않는다. 인증 주체에서만 꺼낸다.
        SuggestionResDTO.Listing result = suggestionQueryService.getMySuggestions(
                userDetails.getMemberId(), cursor, size == null ? DEFAULT_PAGE_SIZE : size);
        return ApiResponse.onSuccess(SuggestionSuccessCode.SUGGESTION_LIST_FETCH_SUCCESS, result);
    }
}
