package com.lirouti.domain.chat.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lirouti.domain.chat.controller.docs.ChatAdminControllerDocs;
import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.exception.code.success.ChatSuccessCode;
import com.lirouti.domain.chat.service.ChatEmoticonAdminService;
import com.lirouti.domain.chat.service.query.ChatQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/chat/emoticons")
public class ChatAdminController implements ChatAdminControllerDocs {
    private final ChatQueryService chatQueryService;
    private final ChatEmoticonAdminService chatEmoticonAdminService;

    /** 현재 DB 권한 검증을 거쳐 활성·비활성 이모티콘 전체를 반환한다. */
    @Override
    @GetMapping
    public ApiResponse<ChatResDTO.AdminEmoticonList> getAdminEmoticons(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ChatResDTO.AdminEmoticonList result =
                chatQueryService.getAdminEmoticons(userDetails.getMemberId());
        return ApiResponse.onSuccess(
                ChatSuccessCode.ADMIN_EMOTICON_LIST_FETCH_SUCCESS,
                result
        );
    }

    /** JSON metadata와 파일 part를 서비스 소유 이모티콘 등록 흐름에 전달한다. */
    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatResDTO.AdminEmoticon> registerEmoticon(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("metadata") ChatReqDTO.RegisterEmoticon metadata,
            @RequestPart("file") MultipartFile file
    ) {
        ChatResDTO.AdminEmoticon result = chatEmoticonAdminService.registerEmoticon(
                userDetails.getMemberId(),
                metadata,
                file.getContentType(),
                file.getSize(),
                file
        );
        return ApiResponse.onSuccess(ChatSuccessCode.EMOTICON_CREATE_SUCCESS, result);
    }

    /** 재활성화 검증과 이모티콘 비활성화가 적용되는 상태 변경을 요청한다. */
    @Override
    @PatchMapping("/{emoticonId}/status")
    public ApiResponse<Void> updateEmoticonStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long emoticonId,
            @Valid @RequestBody ChatReqDTO.UpdateEmoticonStatus request
    ) {
        chatEmoticonAdminService.updateEmoticonStatus(
                userDetails.getMemberId(),
                emoticonId,
                request.active()
        );
        return ApiResponse.onSuccess(ChatSuccessCode.EMOTICON_STATUS_UPDATE_SUCCESS, null);
    }
}
