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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media")
public class MediaController implements MediaControllerDocs {
    private final MediaService mediaService;

    @Override
    @PostMapping("/presigned-url")
    public ApiResponse<MediaResDTO.PresignedUrl> issuePresignedUrl(
            @Valid @RequestBody MediaReqDTO.PresignedUrl request
    ) {
        MediaResDTO.PresignedUrl response = mediaService.issuePresignedUrl(request);
        return ApiResponse.onSuccess(MediaSuccessCode.PRESIGNED_URL_ISSUE_SUCCESS, response);
    }
}
