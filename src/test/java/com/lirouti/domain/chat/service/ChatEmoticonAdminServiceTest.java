package com.lirouti.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamSource;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatEmoticonAdminService 테스트")
class ChatEmoticonAdminServiceTest {
    private static final Long ADMIN_ID = 1L;
    private static final Long EMOTICON_ID = 10L;
    private static final String CODE = "BASIC_HELLO_01";
    private static final String MEDIA_KEY =
            "chat-emoticons/2026/08/08/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.png";
    private static final String CONTENT_TYPE = "image/png";
    private static final String ASSET_URL = "https://signed.example/emoticon";
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private ChatEmoticonRepository chatEmoticonRepository;
    @Mock
    private ChatCommandService chatCommandService;
    @Mock
    private MediaService mediaService;
    @Mock
    private Validator validator;
    @Mock
    private Member admin;

    @InjectMocks
    private ChatEmoticonAdminService adminService;

    @Test
    @DisplayName("관리자 검증 후 S3, URL, DB 순서로 이모티콘을 등록한다")
    void registerEmoticon_ValidRequest_UploadsThenSaves() {
        ChatReqDTO.RegisterEmoticon request = request();
        InputStreamSource source = source();
        ChatEmoticon saved = emoticon(true);
        givenActiveAdmin();
        when(validator.validate(request)).thenReturn(Set.of());
        when(mediaService.uploadServiceOwnedImage(
                MediaPurpose.CHAT_EMOTICON,
                CONTENT_TYPE,
                CONTENT_TYPE,
                PNG.length,
                source
        )).thenReturn(new MediaService.UploadedMedia(MEDIA_KEY, CONTENT_TYPE));
        when(mediaService.resolveViewUrl(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON))
                .thenReturn(ASSET_URL);
        when(chatCommandService.createEmoticonMetadata(request, MEDIA_KEY, CONTENT_TYPE))
                .thenReturn(saved);

        ChatResDTO.AdminEmoticon result = adminService.registerEmoticon(
                ADMIN_ID,
                request,
                CONTENT_TYPE,
                PNG.length,
                source
        );

        assertThat(result.code()).isEqualTo(CODE);
        assertThat(result.assetUrl()).isEqualTo(ASSET_URL);
        assertThat(result.active()).isTrue();
        assertThat(result.animated()).isFalse();
        InOrder order = inOrder(
                memberQueryService,
                chatEmoticonRepository,
                mediaService,
                chatCommandService
        );
        order.verify(memberQueryService).getActiveMember(ADMIN_ID);
        order.verify(chatEmoticonRepository).existsByCode(CODE);
        order.verify(mediaService).uploadServiceOwnedImage(
                MediaPurpose.CHAT_EMOTICON,
                CONTENT_TYPE,
                CONTENT_TYPE,
                PNG.length,
                source
        );
        order.verify(mediaService).resolveViewUrl(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);
        order.verify(chatCommandService)
                .createEmoticonMetadata(request, MEDIA_KEY, CONTENT_TYPE);
    }

    @Test
    @DisplayName("현재 DB role이 관리자가 아니면 등록 정보를 검사하지 않는다")
    void registerEmoticon_CurrentRoleIsUser_Throws403BeforeValidation() {
        when(memberQueryService.getActiveMember(ADMIN_ID)).thenReturn(admin);
        when(admin.getRole()).thenReturn(Role.ROLE_USER);

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request(),
                CONTENT_TYPE,
                PNG.length,
                source()
        )).isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);

        verifyNoInteractions(validator, chatEmoticonRepository, mediaService, chatCommandService);
    }

    @Test
    @DisplayName("비활성 관리자면 등록 정보를 검사하지 않는다")
    void registerEmoticon_InactiveAdmin_PreservesMemberException() {
        MemberException failure = new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        when(memberQueryService.getActiveMember(ADMIN_ID)).thenThrow(failure);

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request(),
                CONTENT_TYPE,
                PNG.length,
                source()
        )).isSameAs(failure);

        verifyNoInteractions(validator, chatEmoticonRepository, mediaService, chatCommandService);
    }

    @Test
    @DisplayName("유효하지 않은 metadata는 중복 조회와 S3 업로드 전에 거부한다")
    void registerEmoticon_InvalidMetadata_Throws400BeforeUpload() {
        ChatReqDTO.RegisterEmoticon request = request();
        givenActiveAdmin();
        @SuppressWarnings("unchecked")
        ConstraintViolation<ChatReqDTO.RegisterEmoticon> violation =
                mock(ConstraintViolation.class);
        when(validator.validate(request)).thenReturn(Set.of(violation));

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request,
                CONTENT_TYPE,
                PNG.length,
                source()
        )).isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.BAD_REQUEST);

        verifyNoInteractions(chatEmoticonRepository, mediaService, chatCommandService);
    }

    @Test
    @DisplayName("중복 code는 S3 업로드 전에 거부한다")
    void registerEmoticon_DuplicateCode_Throws409BeforeUpload() {
        ChatReqDTO.RegisterEmoticon request = request();
        givenActiveAdmin();
        when(validator.validate(request)).thenReturn(Set.of());
        when(chatEmoticonRepository.existsByCode(CODE)).thenReturn(true);

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request,
                CONTENT_TYPE,
                PNG.length,
                source()
        )).isInstanceOf(ChatException.class)
                .extracting("code")
                .isEqualTo(ChatErrorCode.DUPLICATE_EMOTICON_CODE);

        verifyNoInteractions(mediaService, chatCommandService);
    }

    @Test
    @DisplayName("S3 업로드 실패 시 DB 저장과 보상 삭제를 시도하지 않는다")
    void registerEmoticon_UploadFailure_DoesNotSaveOrCompensate() {
        ChatReqDTO.RegisterEmoticon request = request();
        InputStreamSource source = source();
        MediaException failure = new MediaException(MediaErrorCode.MEDIA_UPLOAD_FAILED);
        givenActiveAdmin();
        when(validator.validate(request)).thenReturn(Set.of());
        when(mediaService.uploadServiceOwnedImage(
                MediaPurpose.CHAT_EMOTICON,
                CONTENT_TYPE,
                CONTENT_TYPE,
                PNG.length,
                source
        )).thenThrow(failure);

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request,
                CONTENT_TYPE,
                PNG.length,
                source
        )).isSameAs(failure);

        verify(mediaService, never()).resolveViewUrl(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);
        verify(mediaService, never())
                .deleteServiceOwnedMedia(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);
        verifyNoInteractions(chatCommandService);
    }

    @Test
    @DisplayName("조회 URL 생성 실패 시 업로드한 exact key를 삭제하고 DB를 호출하지 않는다")
    void registerEmoticon_ViewUrlFailure_DeletesUploadedObject() {
        ChatReqDTO.RegisterEmoticon request = request();
        InputStreamSource source = source();
        MediaException failure = new MediaException(MediaErrorCode.PRESIGNED_URL_ISSUE_FAILED);
        givenUpload(request, source);
        when(mediaService.resolveViewUrl(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON))
                .thenThrow(failure);

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request,
                CONTENT_TYPE,
                PNG.length,
                source
        )).isSameAs(failure);

        verify(mediaService).deleteServiceOwnedMedia(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);
        verifyNoInteractions(chatCommandService);
    }

    @Test
    @DisplayName("DB 저장 실패 시 업로드한 exact key를 삭제하고 원래 예외를 반환한다")
    void registerEmoticon_DatabaseFailure_DeletesUploadedObject() {
        ChatReqDTO.RegisterEmoticon request = request();
        InputStreamSource source = source();
        ChatException failure = new ChatException(ChatErrorCode.DUPLICATE_EMOTICON_CODE);
        givenUploadAndViewUrl(request, source);
        when(chatCommandService.createEmoticonMetadata(request, MEDIA_KEY, CONTENT_TYPE))
                .thenThrow(failure);

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request,
                CONTENT_TYPE,
                PNG.length,
                source
        )).isSameAs(failure);

        verify(mediaService).deleteServiceOwnedMedia(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);
    }

    @Test
    @DisplayName("보상 삭제도 실패하면 원래 DB 예외를 유지하고 삭제 예외를 suppressed로 남긴다")
    void registerEmoticon_CompensationFailure_PreservesOriginalFailure() {
        ChatReqDTO.RegisterEmoticon request = request();
        InputStreamSource source = source();
        ChatException original = new ChatException(ChatErrorCode.DUPLICATE_EMOTICON_CODE);
        MediaException compensation = new MediaException(MediaErrorCode.MEDIA_DELETE_FAILED);
        givenUploadAndViewUrl(request, source);
        when(chatCommandService.createEmoticonMetadata(request, MEDIA_KEY, CONTENT_TYPE))
                .thenThrow(original);
        doThrow(compensation)
                .when(mediaService)
                .deleteServiceOwnedMedia(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);

        assertThatThrownBy(() -> adminService.registerEmoticon(
                ADMIN_ID,
                request,
                CONTENT_TYPE,
                PNG.length,
                source
        )).isSameAs(original);
        assertThat(original.getSuppressed()).containsExactly(compensation);
    }

    @Test
    @DisplayName("활성화는 S3 key와 bytes 검증 후 짧은 DB transaction을 호출한다")
    void updateEmoticonStatus_Activate_ValidatesS3BeforeDatabaseChange() {
        ChatEmoticon emoticon = emoticon(false);
        givenActiveAdmin();
        when(chatEmoticonRepository.findById(EMOTICON_ID))
                .thenReturn(Optional.of(emoticon));

        adminService.updateEmoticonStatus(ADMIN_ID, EMOTICON_ID, true);

        InOrder order = inOrder(mediaService, chatCommandService);
        order.verify(mediaService).validateMediaKey(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);
        order.verify(mediaService).validateUploadedBytes(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON);
        order.verify(chatCommandService).updateEmoticonStatus(EMOTICON_ID, true);
    }

    @Test
    @DisplayName("비활성화 조회와 상태 변경은 CommandService에 위임한다")
    void updateEmoticonStatus_Deactivate_DelegatesWithoutLookup() {
        givenActiveAdmin();

        adminService.updateEmoticonStatus(ADMIN_ID, EMOTICON_ID, false);

        verifyNoInteractions(chatEmoticonRepository, mediaService);
        verify(chatCommandService).updateEmoticonStatus(EMOTICON_ID, false);
    }

    @Test
    @DisplayName("존재하지 않는 이모티콘은 S3와 DB 상태 변경 전에 404로 거부한다")
    void updateEmoticonStatus_NotFound_Throws404() {
        givenActiveAdmin();
        when(chatEmoticonRepository.findById(EMOTICON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateEmoticonStatus(
                ADMIN_ID,
                EMOTICON_ID,
                true
        )).isInstanceOf(ChatException.class)
                .extracting("code")
                .isEqualTo(ChatErrorCode.EMOTICON_NOT_FOUND);

        verifyNoInteractions(mediaService, chatCommandService);
    }

    private void givenActiveAdmin() {
        when(memberQueryService.getActiveMember(ADMIN_ID)).thenReturn(admin);
        when(admin.getRole()).thenReturn(Role.ROLE_ADMIN);
    }

    private void givenUpload(
            ChatReqDTO.RegisterEmoticon request,
            InputStreamSource source
    ) {
        givenActiveAdmin();
        when(validator.validate(request)).thenReturn(Set.of());
        when(mediaService.uploadServiceOwnedImage(
                MediaPurpose.CHAT_EMOTICON,
                CONTENT_TYPE,
                CONTENT_TYPE,
                PNG.length,
                source
        )).thenReturn(new MediaService.UploadedMedia(MEDIA_KEY, CONTENT_TYPE));
    }

    private void givenUploadAndViewUrl(
            ChatReqDTO.RegisterEmoticon request,
            InputStreamSource source
    ) {
        givenUpload(request, source);
        when(mediaService.resolveViewUrl(MEDIA_KEY, MediaPurpose.CHAT_EMOTICON))
                .thenReturn(ASSET_URL);
    }

    private ChatReqDTO.RegisterEmoticon request() {
        return new ChatReqDTO.RegisterEmoticon(CODE, CONTENT_TYPE, 10);
    }

    private InputStreamSource source() {
        return () -> new ByteArrayInputStream(PNG);
    }

    private ChatEmoticon emoticon(boolean active) {
        return ChatEmoticon.builder()
                .code(CODE)
                .assetKey(MEDIA_KEY)
                .contentType(CONTENT_TYPE)
                .animated(false)
                .active(active)
                .displayOrder(10)
                .build();
    }
}
