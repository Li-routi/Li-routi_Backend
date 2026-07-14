package com.lirouti.domain.image.controller.docs;

import com.lirouti.domain.image.dto.request.ImageReqDTO;
import com.lirouti.domain.image.dto.response.ImageResDTO;
import com.lirouti.global.apiPayload.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Image", description = "이미지 업로드 API")
public interface ImageControllerDocs {

    @Operation(
            summary = "이미지 업로드 URL 발급",
            description = """
                    S3에 직접 업로드할 수 있는 presigned URL을 발급합니다.

                    클라이언트는 발급받은 `uploadUrl`로 **PUT** 요청을 보내 이미지를 업로드합니다.
                    이때 요청에 사용한 `contentType`과 `contentLength`를 헤더에 그대로 실어야 합니다.
                    값이 다르면 S3가 업로드를 거부합니다.

                    업로드에 성공하면 `imageKey`를 해당 도메인 API(예: 챌린지 인증)에 전달해 저장합니다.
                    발급된 URL은 만료 시간이 지나면 사용할 수 없습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업로드 URL 발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 이미지 형식(jpeg, png, webp만 허용)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413",
                    description = "최대 업로드 용량 초과"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "업로드 URL 발급 실패"
            )
    })
    ApiResponse<ImageResDTO.PresignedUrl> issuePresignedUrl(ImageReqDTO.PresignedUrl request);
}
