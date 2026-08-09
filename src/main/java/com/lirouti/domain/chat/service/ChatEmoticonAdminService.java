package com.lirouti.domain.chat.service;

import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

import com.lirouti.domain.chat.converter.ChatConverter;
import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatEmoticonAdminService {
    private static final MediaPurpose EMOTICON_PURPOSE = MediaPurpose.CHAT_EMOTICON;

    private final MemberQueryService memberQueryService;
    private final ChatEmoticonRepository chatEmoticonRepository;
    private final ChatCommandService chatCommandService;
    private final MediaService mediaService;
    private final Validator validator;

    /**
     * 관리자와 입력을 검증한 뒤 S3 업로드, 조회 URL 생성, DB 저장 순서로 등록한다.
     * DB 저장 전 실패하면 업로드한 exact key를 보상 삭제하며 원래 예외를 유지한다.
     */
    public ChatResDTO.AdminEmoticon registerEmoticon(
            Long adminId,
            ChatReqDTO.RegisterEmoticon request,
            String fileContentType,
            long contentLength,
            InputStreamSource contentSource
    ) {
        validateActiveAdmin(adminId);
        validateRegistrationRequest(request);
        if (chatEmoticonRepository.existsByCode(request.code())) {
            throw new ChatException(ChatErrorCode.DUPLICATE_EMOTICON_CODE);
        }

        MediaService.UploadedMedia uploaded = mediaService.uploadServiceOwnedImage(
                EMOTICON_PURPOSE,
                request.contentType(),
                fileContentType,
                contentLength,
                contentSource
        );

        String assetUrl;
        ChatEmoticon emoticon;
        try {
            assetUrl = mediaService.resolveViewUrl(
                    uploaded.mediaKey(),
                    EMOTICON_PURPOSE
            );
            emoticon = chatCommandService.createEmoticonMetadata(
                    request,
                    uploaded.mediaKey(),
                    uploaded.contentType()
            );
        } catch (RuntimeException originalFailure) {
            compensateUploadedMedia(uploaded.mediaKey(), originalFailure);
            throw originalFailure;
        }
        return ChatConverter.toAdminEmoticon(emoticon, assetUrl);
    }

    /**
     * 현재 관리자 권한을 다시 확인하고 상태를 변경한다.
     * 활성화는 S3 object의 key와 실제 bytes를 transaction 밖에서 먼저 검증한다.
     */
    public void updateEmoticonStatus(
            Long adminId,
            Long emoticonId,
            boolean active
    ) {
        validateActiveAdmin(adminId);
        if (active) {
            ChatEmoticon emoticon = chatEmoticonRepository.findById(emoticonId)
                    .orElseThrow(() -> new ChatException(ChatErrorCode.EMOTICON_NOT_FOUND));
            mediaService.validateMediaKey(emoticon.getAssetKey(), EMOTICON_PURPOSE);
            mediaService.validateUploadedBytes(emoticon.getAssetKey(), EMOTICON_PURPOSE);
        }
        chatCommandService.updateEmoticonStatus(emoticonId, active);
    }

    private void validateActiveAdmin(Long adminId) {
        Member member = memberQueryService.getActiveMember(adminId);
        if (member.getRole() != Role.ROLE_ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private void validateRegistrationRequest(ChatReqDTO.RegisterEmoticon request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
    }

    private void compensateUploadedMedia(
            String mediaKey,
            RuntimeException originalFailure
    ) {
        try {
            mediaService.deleteServiceOwnedMedia(mediaKey, EMOTICON_PURPOSE);
        } catch (RuntimeException compensationFailure) {
            if (compensationFailure != originalFailure) {
                originalFailure.addSuppressed(compensationFailure);
            }
            log.error("이모티콘 등록 실패 후 S3 보상 삭제에도 실패했습니다. mediaKey={}",
                    mediaKey, compensationFailure);
        }
    }
}
