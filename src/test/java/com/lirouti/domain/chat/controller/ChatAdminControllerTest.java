package com.lirouti.domain.chat.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.service.ChatEmoticonAdminService;
import com.lirouti.domain.chat.service.query.ChatQueryService;
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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatAdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtExceptionFilter.class,
        AuthenticationEntryPointImpl.class, AccessDeniedHandlerImpl.class,
        ApiErrorResponseWriter.class})
@DisplayName("ChatAdminController HTTP 계약 테스트")
class ChatAdminControllerTest {
    private static final Long ADMIN_ID = 1L;
    private static final Long EMOTICON_ID = 10L;
    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatQueryService chatQueryService;

    @MockitoBean
    private ChatEmoticonAdminService chatEmoticonAdminService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RedisUtil redisUtil;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("관리자는 활성·비활성 이모티콘 전체를 조회하고 assetKey를 받지 않는다")
    void getAdminEmoticons_Admin_ReturnsAllWithoutAssetKey() throws Exception {
        // given
        ChatResDTO.AdminEmoticonList response = ChatResDTO.AdminEmoticonList.builder()
                .emoticons(List.of(adminEmoticon(false)))
                .build();
        when(chatQueryService.getAdminEmoticons(ADMIN_ID)).thenReturn(response);

        // when
        var result = mockMvc.perform(get("/api/admin/chat/emoticons")
                .with(user(adminPrincipal())));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHAT200_5"))
                .andExpect(jsonPath("$.result.emoticons[0].id").value(EMOTICON_ID))
                .andExpect(jsonPath("$.result.emoticons[0].active").value(false))
                .andExpect(jsonPath("$.result.emoticons[0].assetUrl")
                        .value("https://example.com/emoticon.png"))
                .andExpect(jsonPath("$.result.emoticons[0].assetKey").doesNotExist());
        verify(chatQueryService).getAdminEmoticons(ADMIN_ID);
    }

    @Test
    @DisplayName("올바른 multipart 요청은 metadata와 파일을 전달하고 201을 반환한다")
    void registerEmoticon_ValidMultipart_ReturnsCreated() throws Exception {
        // given
        MockMultipartFile metadata = metadataPart("""
                {"code":"BASIC_HELLO_01","contentType":"image/png","displayOrder":10}
                """);
        MockMultipartFile file = imagePart();
        ChatReqDTO.RegisterEmoticon request = new ChatReqDTO.RegisterEmoticon(
                "BASIC_HELLO_01",
                MediaType.IMAGE_PNG_VALUE,
                10
        );
        when(chatEmoticonAdminService.registerEmoticon(
                eq(ADMIN_ID),
                eq(request),
                eq(MediaType.IMAGE_PNG_VALUE),
                eq((long) PNG_BYTES.length),
                same(file)
        )).thenReturn(adminEmoticon(true));

        // when
        var result = mockMvc.perform(multipart("/api/admin/chat/emoticons")
                .file(metadata)
                .file(file)
                .with(user(adminPrincipal())));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHAT201_1"))
                .andExpect(jsonPath("$.result.code").value("BASIC_HELLO_01"))
                .andExpect(jsonPath("$.result.active").value(true))
                .andExpect(jsonPath("$.result.assetKey").doesNotExist());
        verify(chatEmoticonAdminService).registerEmoticon(
                eq(ADMIN_ID),
                eq(request),
                eq(MediaType.IMAGE_PNG_VALUE),
                eq((long) PNG_BYTES.length),
                same(file)
        );
    }

    @Test
    @DisplayName("이모티콘 code 형식이 잘못되면 400이고 등록 서비스를 호출하지 않는다")
    void registerEmoticon_InvalidMetadata_ReturnsBadRequest() throws Exception {
        // given
        MockMultipartFile metadata = metadataPart("""
                {"code":"basic-hello","contentType":"image/png","displayOrder":10}
                """);

        // when
        var result = mockMvc.perform(multipart("/api/admin/chat/emoticons")
                .file(metadata)
                .file(imagePart())
                .with(user(adminPrincipal())));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(chatEmoticonAdminService);
    }

    @Test
    @DisplayName("관리자는 이모티콘을 비활성화하고 빈 성공 응답을 받는다")
    void updateEmoticonStatus_Admin_ReturnsSuccess() throws Exception {
        // when
        var result = mockMvc.perform(patch(
                        "/api/admin/chat/emoticons/{emoticonId}/status",
                        EMOTICON_ID
                )
                .with(user(adminPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"active":false}
                        """));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHAT200_6"))
                .andExpect(jsonPath("$.result").doesNotExist());
        verify(chatEmoticonAdminService).updateEmoticonStatus(
                ADMIN_ID,
                EMOTICON_ID,
                false
        );
    }

    @Test
    @DisplayName("활성 상태가 없으면 400이고 상태 변경 서비스를 호출하지 않는다")
    void updateEmoticonStatus_MissingActive_ReturnsBadRequest() throws Exception {
        // when
        var result = mockMvc.perform(patch(
                        "/api/admin/chat/emoticons/{emoticonId}/status",
                        EMOTICON_ID
                )
                .with(user(adminPrincipal()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verify(chatEmoticonAdminService, never())
                .updateEmoticonStatus(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("일반 회원은 403이고 관리자 서비스를 호출하지 않는다")
    void registerEmoticon_User_ReturnsForbidden() throws Exception {
        // when
        var result = mockMvc.perform(multipart("/api/admin/chat/emoticons")
                .file(metadataPart("""
                        {"code":"BASIC_HELLO_01","contentType":"image/png","displayOrder":10}
                        """))
                .file(imagePart())
                .with(user(new CustomUserDetails(2L, Role.ROLE_USER))));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH403_1"));
        verifyNoInteractions(chatEmoticonAdminService);
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401이고 조회 서비스를 호출하지 않는다")
    void getAdminEmoticons_Anonymous_ReturnsUnauthorized() throws Exception {
        // when
        var result = mockMvc.perform(get("/api/admin/chat/emoticons"));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
        verifyNoInteractions(chatQueryService);
    }

    private ChatResDTO.AdminEmoticon adminEmoticon(boolean active) {
        return ChatResDTO.AdminEmoticon.builder()
                .id(EMOTICON_ID)
                .code("BASIC_HELLO_01")
                .assetUrl("https://example.com/emoticon.png")
                .contentType(MediaType.IMAGE_PNG_VALUE)
                .animated(false)
                .active(active)
                .displayOrder(10)
                .build();
    }

    private MockMultipartFile metadataPart(String json) {
        return new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile(
                "file",
                "emoticon.png",
                MediaType.IMAGE_PNG_VALUE,
                PNG_BYTES
        );
    }

    private CustomUserDetails adminPrincipal() {
        return new CustomUserDetails(ADMIN_ID, Role.ROLE_ADMIN);
    }
}
