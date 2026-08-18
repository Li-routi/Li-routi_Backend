package com.lirouti.domain.mypage.controller;

import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.mypage.dto.response.SuggestionResDTO;
import com.lirouti.domain.mypage.exception.SuggestionException;
import com.lirouti.domain.mypage.exception.code.error.SuggestionErrorCode;
import com.lirouti.domain.mypage.service.command.SuggestionCommandService;
import com.lirouti.domain.mypage.service.query.SuggestionQueryService;
import com.lirouti.global.apiPayload.ApiErrorResponseWriter;
import com.lirouti.global.auth.AccessDeniedHandlerImpl;
import com.lirouti.global.auth.AuthenticationEntryPointImpl;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.auth.filter.JwtAuthFilter;
import com.lirouti.global.auth.filter.JwtExceptionFilter;
import com.lirouti.global.config.SecurityConfig;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 건의 API 의 HTTP 계약.
 *
 * <p>서비스 테스트는 예외 클래스만 확인하므로 <b>상태 코드와 응답 코드가 계약대로 나가는지</b>
 * 알 수 없다. 특히 {@code size} 상한은 {@code @Validated} 가 던지는 예외를 전역 처리기가 받아야
 * 400 이 되는데, 그 연결은 코드를 읽어서는 확인되지 않는다.
 */
@WebMvcTest(SuggestionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtExceptionFilter.class,
        AuthenticationEntryPointImpl.class, AccessDeniedHandlerImpl.class,
        ApiErrorResponseWriter.class})
@DisplayName("건의 API HTTP 계약")
class SuggestionControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final String PATH = "/api/members/me/suggestions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private SuggestionQueryService suggestionQueryService;
    @MockitoBean private SuggestionCommandService suggestionCommandService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private RedisUtil redisUtil;
    @MockitoBean private JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean private com.lirouti.domain.achievement.service.command.MidnightAccessCommandService midnightAccessCommandService;

    // ── 목록 ──

    @Test
    @DisplayName("size 를 안 주면 기본값 20 으로 조회한다")
    void list_UsesDefaultSize() throws Exception {
        when(suggestionQueryService.getMySuggestions(eq(MEMBER_ID), any(), anyInt()))
                .thenReturn(SuggestionResDTO.Listing.builder()
                        .suggestions(List.of()).hasNext(false).build());

        mockMvc.perform(get(PATH).with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUGGESTION200_2"));

        verify(suggestionQueryService).getMySuggestions(eq(MEMBER_ID), eq(null), eq(20));
    }

    /**
     * <b>상한이 없으면 큰 값을 넣는 것만으로 자기 건의 전부를 한 번에 끌어갈 수 있다.</b>
     * 조용히 깎지 않고 거절하는 것이 계약이다.
     */
    @Test
    @DisplayName("size 가 범위를 벗어나면 400 과 SUGGESTION400_1 이 나간다")
    void list_RejectsOutOfRangeSize() throws Exception {
        when(suggestionQueryService.getMySuggestions(eq(MEMBER_ID), any(), eq(51)))
                .thenThrow(new SuggestionException(SuggestionErrorCode.INVALID_PAGE_SIZE));

        mockMvc.perform(get(PATH).param("size", "51")
                        .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUGGESTION400_1"));
    }

    @Test
    @DisplayName("size 를 숫자가 아닌 값으로 주면 400 이다")
    void list_RejectsNonNumericSize() throws Exception {
        mockMvc.perform(get(PATH).param("size", "많이")
                        .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))))
                .andExpect(status().isBadRequest());

        verify(suggestionQueryService, never()).getMySuggestions(anyLong(), any(), anyInt());
    }

    /** 회원 식별자를 요청에서 받지 않는다는 것이 이 API 의 격리 규칙이다. */
    @Test
    @DisplayName("인증이 없으면 401 이고 서비스까지 가지 않는다")
    void list_RequiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        verify(suggestionQueryService, never()).getMySuggestions(anyLong(), any(), anyInt());
    }

    // ── 등록 ──

    @Test
    @DisplayName("등록에 성공하면 201 과 등록 성공 코드가 나간다")
    void create_ReturnsCreated() throws Exception {
        when(suggestionCommandService.create(eq(MEMBER_ID), eq(1L), eq("내용")))
                .thenReturn(SuggestionResDTO.Suggestion.builder().id(10L).content("내용").build());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"content\":\"내용\"}")
                        .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUGGESTION201_1"));
    }

    @Test
    @DisplayName("본문이 비면 400 이고 서비스를 부르지 않는다")
    void create_RejectsBlankContent() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"content\":\"  \"}")
                        .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))))
                .andExpect(status().isBadRequest());

        verify(suggestionCommandService, never()).create(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("내려간 분류로 등록하면 409 와 SUGGESTION409_1 이 나간다")
    void create_RejectsInactiveCategory() throws Exception {
        when(suggestionCommandService.create(eq(MEMBER_ID), eq(4L), any()))
                .thenThrow(new SuggestionException(SuggestionErrorCode.CATEGORY_NOT_ACTIVE));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":4,\"content\":\"내용\"}")
                        .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUGGESTION409_1"));
    }
}
