package com.lirouti.domain.image.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.image.controller.docs.ImageControllerDocs;
import com.lirouti.domain.image.dto.request.ImageReqDTO;
import com.lirouti.domain.image.dto.response.ImageResDTO;
import com.lirouti.domain.image.exception.code.success.ImageSuccessCode;
import com.lirouti.domain.image.service.ImageService;
import com.lirouti.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageController implements ImageControllerDocs {
    private final ImageService imageService;

    @Override
    @PostMapping("/presigned-url")
    public ApiResponse<ImageResDTO.PresignedUrl> issuePresignedUrl(
            @Valid @RequestBody ImageReqDTO.PresignedUrl request
    ) {
        ImageResDTO.PresignedUrl response = imageService.issuePresignedUrl(request);
        return ApiResponse.onSuccess(ImageSuccessCode.PRESIGNED_URL_ISSUE_SUCCESS, response);
    }
}
