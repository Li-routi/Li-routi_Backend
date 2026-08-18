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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 건의.
 *
 * <p><b>경로가 {@code /api/members/me} 아래인 것이 곧 격리 규칙이다.</b> 회원 식별자를 경로에서
 * 받지 않으므로 남의 것을 볼 방법이 없다 — {@code /api/members/{memberId}/suggestions} 로 두면
 * 그 값을 검증하는 코드가 매번 필요해진다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/suggestions")
public class SuggestionController implements SuggestionControllerDocs {

    /** 값을 안 주면 이만큼. 상한 검증은 서비스가 한다. */
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
    // 성공 코드의 HttpStatus 는 본문에만 실린다. 실제 응답 상태는 여기서 정한다.
    @ResponseStatus(HttpStatus.CREATED)
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
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 회원 식별자를 요청에서 받지 않는다. 인증 주체에서만 꺼낸다.
        SuggestionResDTO.Listing result = suggestionQueryService.getMySuggestions(
                userDetails.getMemberId(), cursor, size == null ? DEFAULT_PAGE_SIZE : size);
        return ApiResponse.onSuccess(SuggestionSuccessCode.SUGGESTION_LIST_FETCH_SUCCESS, result);
    }
}
