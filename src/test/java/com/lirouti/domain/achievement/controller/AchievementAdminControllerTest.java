package com.lirouti.domain.achievement.controller;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.service.AchievementBadgeImageAdminService;
import com.lirouti.domain.achievement.service.command.MidnightAccessCommandService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AchievementAdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtExceptionFilter.class,
        AuthenticationEntryPointImpl.class, AccessDeniedHandlerImpl.class,
        ApiErrorResponseWriter.class})
@DisplayName("AchievementAdminController HTTP 계약 테스트")
class AchievementAdminControllerTest {
    private static final Long ADMIN_ID = 1L;
    private static final Long ACHIEVEMENT_ID = 21L;
    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AchievementBadgeImageAdminService achievementBadgeImageAdminService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RedisUtil redisUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private MidnightAccessCommandService midnightAccessCommandService;

    @Test
    @DisplayName("관리자는 이미지 파일을 전달하고 업적 뱃지 URL을 받는다")
    void uploadBadgeImage_ValidMultipart_ReturnsSuccess() throws Exception {
        MockMultipartFile file = imagePart();
        AchievementResDTO.AdminBadgeImage response = AchievementResDTO.AdminBadgeImage.builder()
                .achievementId(ACHIEVEMENT_ID)
                .code("ACH-AC-001")
                .badgeImageUrl("https://cdn.example.com/achievement.png")
                .build();
        when(achievementBadgeImageAdminService.uploadBadgeImage(
                eq(ADMIN_ID),
                eq(ACHIEVEMENT_ID),
                eq(MediaType.IMAGE_PNG_VALUE),
                eq((long) PNG_BYTES.length),
                same(file)
        )).thenReturn(response);

        var result = mockMvc.perform(multipart(
                        "/api/admin/achievements/{achievementId}/badge-image", ACHIEVEMENT_ID)
                .file(file)
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .with(user(new CustomUserDetails(ADMIN_ID, Role.ROLE_ADMIN))));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("ACHIEVEMENT200_3"))
                .andExpect(jsonPath("$.result.achievementId").value(ACHIEVEMENT_ID))
                .andExpect(jsonPath("$.result.badgeImageUrl")
                        .value("https://cdn.example.com/achievement.png"))
                .andExpect(jsonPath("$.result.badgeImageKey").doesNotExist());
        verify(achievementBadgeImageAdminService).uploadBadgeImage(
                eq(ADMIN_ID),
                eq(ACHIEVEMENT_ID),
                eq(MediaType.IMAGE_PNG_VALUE),
                eq((long) PNG_BYTES.length),
                same(file)
        );
    }

    @Test
    @DisplayName("일반 회원은 관리자 업로드 API에 접근할 수 없다")
    void uploadBadgeImage_User_ReturnsForbidden() throws Exception {
        var result = mockMvc.perform(multipart(
                        "/api/admin/achievements/{achievementId}/badge-image", ACHIEVEMENT_ID)
                .file(imagePart())
                .with(user(new CustomUserDetails(2L, Role.ROLE_USER))));

        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH403_1"));
        verifyNoInteractions(achievementBadgeImageAdminService);
    }

    @Test
    @DisplayName("file part가 없으면 400이고 관리자 서비스를 호출하지 않는다")
    void uploadBadgeImage_MissingFile_ReturnsBadRequest() throws Exception {
        var result = mockMvc.perform(multipart(
                        "/api/admin/achievements/{achievementId}/badge-image", ACHIEVEMENT_ID)
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .with(user(new CustomUserDetails(ADMIN_ID, Role.ROLE_ADMIN))));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verify(achievementBadgeImageAdminService, never())
                .uploadBadgeImage(eq(ADMIN_ID), eq(ACHIEVEMENT_ID),
                        eq(MediaType.IMAGE_PNG_VALUE), eq(0L), same(null));
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile(
                "file",
                "achievement.png",
                MediaType.IMAGE_PNG_VALUE,
                PNG_BYTES
        );
    }
}
