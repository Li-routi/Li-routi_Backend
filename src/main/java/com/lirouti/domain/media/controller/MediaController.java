package com.lirouti.domain.media.controller;

import com.lirouti.domain.media.controller.docs.MediaControllerDocs;
import com.lirouti.domain.media.dto.request.MediaReqDTO;
import com.lirouti.domain.media.dto.response.MediaResDTO;
import com.lirouti.domain.media.exception.code.success.MediaSuccessCode;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media")
public class MediaController implements MediaControllerDocs {
    private final MediaService mediaService;

    @Override
    // 빈도 제한은 여기 @RateLimit이 아니라 MediaService 안에서 건다. 용도(purpose)마다 정책이
    // 갈리는데 그 값이 요청 본문에 있어, 본문을 읽기 전에 도는 인터셉터로는 고를 수 없다.
    @PostMapping("/presigned-url")
    public ApiResponse<MediaResDTO.PresignedUrl> issuePresignedUrl(
            @Valid @RequestBody MediaReqDTO.PresignedUrl request
    ) {
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);
        return ApiResponse.onSuccess(MediaSuccessCode.PRESIGNED_URL_ISSUE_SUCCESS, response);
    }
}
