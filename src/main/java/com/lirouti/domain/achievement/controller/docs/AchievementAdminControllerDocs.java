package com.lirouti.domain.achievement.controller.docs;

import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "관리자 - 업적 관리", description = "서비스 소유 업적 뱃지 이미지 운영 API")
public interface AchievementAdminControllerDocs {

    @Operation(
            summary = "업적 뱃지 이미지 업로드·교체",
            description = """
                    활성 관리자만 기존 업적의 뱃지 이미지를 등록하거나 교체할 수 있습니다.
                    PNG/JPEG/WEBP 파일을 multipart/form-data의 file part로 전송합니다.
                    서버는 업로드 후 DB의 이미지 key를 교체하며, 응답에는 public 조회 URL만 포함합니다.
                    이미지가 없는 업적에도 업로드할 수 있고, badgeYn과 이미지 존재 여부는 별개입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "업적 뱃지 이미지 업로드 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "파일이 없거나 지원하지 않는 이미지 형식"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증되지 않은 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "현재 활성 관리자가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "업적을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413", description = "이미지 파일 크기 상한 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "이미지 bytes 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "S3 업로드 또는 URL 생성 실패")
    })
    ApiResponse<AchievementResDTO.AdminBadgeImage> uploadBadgeImage(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "이미지를 등록할 업적 ID", example = "21") Long achievementId,
            @Parameter(
                    description = "PNG/JPEG/WEBP 업적 뱃지 이미지",
                    required = true,
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
            @RequestPart("file") MultipartFile file
    );
}
