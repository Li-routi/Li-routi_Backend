package com.lirouti.domain.character.controller;

import com.lirouti.domain.achievement.service.command.MidnightAccessCommandService;
import com.lirouti.domain.character.exception.CharacterException;
import com.lirouti.domain.character.exception.code.error.CharacterErrorCode;
import com.lirouti.domain.character.service.command.CharacterSelectionCommandService;
import com.lirouti.domain.character.service.query.CharacterQueryService;
import com.lirouti.domain.member.enums.Role;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 캐릭터 선택의 HTTP 계약.
 *
 * <p>여기서 지키는 것은 <b>없는 캐릭터와 안 연 캐릭터가 같은 응답을 받는다</b>이다. 가르면
 * id 를 넣어 보는 것만으로 어떤 캐릭터가 존재하는지 알 수 있다.
 *
 * <p>서비스 단위 테스트는 예외 클래스만 확인하므로 상태 코드와 응답 코드가 계약대로 나가는지
 * 알 수 없다 — 그것을 여기서 본다.
 */
@WebMvcTest(CharacterController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtExceptionFilter.class,
        AuthenticationEntryPointImpl.class, AccessDeniedHandlerImpl.class,
        ApiErrorResponseWriter.class})
@DisplayName("CharacterController HTTP 계약 테스트")
class CharacterControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long LOCKED_CHARACTER_ID = 2L;
    private static final Long MISSING_CHARACTER_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CharacterQueryService characterQueryService;
    @MockitoBean
    private CharacterSelectionCommandService characterSelectionCommandService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private RedisUtil redisUtil;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean
    private MidnightAccessCommandService midnightAccessCommandService;

    @Test
    @DisplayName("보유한 캐릭터를 고르면 200 과 선택 성공 코드가 나간다")
    void select_OwnedCharacter_ReturnsSuccess() throws Exception {
        // given
        Long ownedCharacterId = 1L;

        // when
        var result = mockMvc.perform(put("/api/characters/selection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"characterId\":" + ownedCharacterId + "}")
                .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHARACTER200_2"));
        verify(characterSelectionCommandService).select(eq(MEMBER_ID), eq(ownedCharacterId));
    }

    @Test
    @DisplayName("안 연 캐릭터를 고르면 404 와 CHARACTER404_1 이 나간다")
    void select_LockedCharacter_Returns404() throws Exception {
        // given
        doThrow(new CharacterException(CharacterErrorCode.CHARACTER_NOT_OWNED))
                .when(characterSelectionCommandService).select(eq(MEMBER_ID), eq(LOCKED_CHARACTER_ID));

        // when
        var result = mockMvc.perform(put("/api/characters/selection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"characterId\":" + LOCKED_CHARACTER_ID + "}")
                .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CHARACTER404_1"));
    }

    /**
     * <b>없는 캐릭터도 같은 답을 받는다.</b> 서비스가 보유 여부만 보고 존재 여부를 가르지 않으므로
     * 같은 예외가 나가고, 그 결과 응답이 위 테스트와 구분되지 않는다.
     */
    @Test
    @DisplayName("없는 캐릭터도 안 연 캐릭터와 똑같은 404 를 받는다")
    void select_MissingCharacter_IsIndistinguishableFromLocked() throws Exception {
        // given
        doThrow(new CharacterException(CharacterErrorCode.CHARACTER_NOT_OWNED))
                .when(characterSelectionCommandService).select(eq(MEMBER_ID), eq(MISSING_CHARACTER_ID));

        // when
        var result = mockMvc.perform(put("/api/characters/selection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"characterId\":" + MISSING_CHARACTER_ID + "}")
                .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CHARACTER404_1"));
    }

    @Test
    @DisplayName("캐릭터 id 가 없으면 서비스를 부르지 않고 400 이다")
    void select_MissingCharacterId_Returns400() throws Exception {
        // when
        var result = mockMvc.perform(put("/api/characters/selection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))));

        // then
        result.andExpect(status().isBadRequest());
        verify(characterSelectionCommandService, org.mockito.Mockito.never())
                .select(anyLong(), anyLong());
    }
}
