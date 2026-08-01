package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.domain.group.service.command.GroupInviteCodeCommandService;
import com.lirouti.domain.group.service.query.GroupInviteCodeQueryService;
import com.lirouti.domain.group.service.query.GroupQueryService;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.apiPayload.ApiErrorResponseWriter;
import com.lirouti.global.auth.AccessDeniedHandlerImpl;
import com.lirouti.global.auth.AuthenticationEntryPointImpl;
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
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtExceptionFilter.class,
        AuthenticationEntryPointImpl.class, AccessDeniedHandlerImpl.class,
        ApiErrorResponseWriter.class})
@DisplayName("모임방 통합 생성 WebMvc 테스트")
class GroupCreationControllerWebTest {
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GroupCommandService groupCommandService;
    @MockitoBean
    private GroupQueryService groupQueryService;
    @MockitoBean
    private GroupInviteCodeCommandService groupInviteCodeCommandService;
    @MockitoBean
    private GroupInviteCodeQueryService groupInviteCodeQueryService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private RedisUtil redisUtil;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("유효한 요청은 201 공통 응답을 반환하며 초대코드를 노출하지 않는다")
    void createGroup_ValidRequest_ReturnsCreatedWithoutInviteCode() throws Exception {
        // given
        GroupResDTO.CreateResult result = GroupResDTO.CreateResult.builder()
                .groupId(10L)
                .name("아침 모임")
                .customCategories(List.of())
                .routines(List.of())
                .assignmentCount(1)
                .build();
        when(groupCommandService.createGroup(eq(MEMBER_ID), any(GroupReqDTO.CreateGroup.class)))
                .thenReturn(result);

        // when & then
        mockMvc.perform(post("/api/groups")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP201_3"))
                .andExpect(jsonPath("$.result.groupId").value(10L))
                .andExpect(jsonPath("$.result.name").value("아침 모임"))
                .andExpect(jsonPath("$.result.inviteCode").doesNotExist())
                .andExpect(jsonPath("$.result.inviteCodeExpiresAt").doesNotExist());
        verify(groupCommandService).createGroup(
                eq(MEMBER_ID), any(GroupReqDTO.CreateGroup.class)
        );
    }

    @Test
    @DisplayName("활성 그룹 참여 상한 예외는 409 그룹 오류 응답으로 변환한다")
    void createGroup_ParticipationLimitExceeded_ReturnsGroupConflict() throws Exception {
        // given
        when(groupCommandService.createGroup(eq(MEMBER_ID), any(GroupReqDTO.CreateGroup.class)))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_PARTICIPATION_LIMIT_EXCEEDED));

        // when & then
        mockMvc.perform(post("/api/groups")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("GROUP409_6"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("인증하지 않은 통합 생성 요청은 기존 보안 정책에 따라 401으로 거부한다")
    void createGroup_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
        verify(groupCommandService, never())
                .createGroup(any(), any(GroupReqDTO.CreateGroup.class));
    }

    @Test
    @DisplayName("잘못된 카테고리 요청은 400 공통 오류 응답으로 변환한다")
    void createGroup_InvalidCategory_ReturnsBadRequest() throws Exception {
        assertValidationError("""
                {
                  "name": "아침 모임",
                  "customCategories": [
                    {"clientKey": "morning", "name": "아침\\n관리", "color": "BLUE"}
                  ],
                  "routines": [
                    {"categoryKey": "morning", "title": "운동", "description": "설명",
                     "schedules": [{"repeatDay": "MONDAY", "startTime": "07:00", "endTime": "08:00"}]}
                  ]
                }
                """);
    }

    @Test
    @DisplayName("초기 루틴이 없는 요청은 400 공통 오류 응답으로 변환한다")
    void createGroup_InvalidRoutine_ReturnsBadRequest() throws Exception {
        assertValidationError("""
                {"name": "아침 모임", "customCategories": [], "routines": []}
                """);
    }

    @Test
    @DisplayName("잘못된 일정 시간 범위는 400 공통 오류 응답으로 변환한다")
    void createGroup_InvalidSchedule_ReturnsBadRequest() throws Exception {
        assertValidationError("""
                {
                  "name": "아침 모임",
                  "customCategories": [],
                  "routines": [
                    {"categoryId": 1, "title": "운동", "description": "설명",
                     "schedules": [{"repeatDay": "MONDAY", "startTime": "08:00", "endTime": "07:00"}]}
                  ]
                }
                """);
    }

    private void assertValidationError(String request) throws Exception {
        mockMvc.perform(post("/api/groups")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result").isString());
        verify(groupCommandService, never())
                .createGroup(any(), any(GroupReqDTO.CreateGroup.class));
    }

    private CustomUserDetails principal() {
        return new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
    }

    private String validRequest() {
        return """
                {
                  "name": "아침 모임",
                  "customCategories": [],
                  "routines": [
                    {"categoryId": 1, "title": "아침 운동", "description": "함께 운동합니다.",
                     "schedules": [{"repeatDay": "MONDAY", "startTime": "07:00", "endTime": "08:00"}]}
                  ]
                }
                """;
    }
}
