package com.lirouti.domain.media.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.media.controller.docs.MediaControllerDocs;
import com.lirouti.domain.media.dto.request.MediaReqDTO;
import com.lirouti.domain.media.dto.response.MediaResDTO;
import com.lirouti.domain.media.exception.code.success.MediaSuccessCode;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.ratelimit.RateLimit;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media")
public class MediaController implements MediaControllerDocs {
    private final MediaService mediaService;

    @Override
    // 발급 자체는 가볍지만 발급받은 URL 하나가 최대 10MB 업로드를 허용한다.
    // 반복 발급 + 반복 업로드로 스토리지를 남용할 수 있어 사용자당 빈도를 제한한다(#23).
    @RateLimit("media-presign")
    @PostMapping("/presigned-url")
    public ApiResponse<MediaResDTO.PresignedUrl> issuePresignedUrl(
            @Valid @RequestBody MediaReqDTO.PresignedUrl request
    ) {
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);
        return ApiResponse.onSuccess(MediaSuccessCode.PRESIGNED_URL_ISSUE_SUCCESS, response);
    }
}
