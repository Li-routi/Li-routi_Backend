package com.lirouti.domain.chat.controller.docs;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "관리자 - 채팅 이모티콘 관리", description = "서비스 소유 채팅 이모티콘 운영 API")
public interface ChatAdminControllerDocs {

    @Operation(
            summary = "관리자용 채팅 이모티콘 목록 조회",
            description = """
                    현재 DB에서 활성 관리자 권한이 확인된 회원만 조회할 수 있습니다.
                    활성·비활성 이모티콘을 displayOrder, id 오름차순으로 반환합니다.
                    private S3 object key는 노출하지 않고 조회용 URL만 제공합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "관리자용 이모티콘 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증되지 않은 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "현재 활성 관리자가 아님")
    })
    ApiResponse<ChatResDTO.AdminEmoticonList> getAdminEmoticons(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
            summary = "채팅 이모티콘 등록",
            description = """
                    metadata JSON과 PNG/JPEG/WEBP 파일을 multipart/form-data로 함께 전송합니다.
                    서버가 파일 형식과 실제 bytes를 검증하고 private S3에 업로드한 뒤
                    활성 상태의 정적 이모티콘 metadata를 저장합니다.

                    ### 에러 코드

                    | code | HTTP | 설명 |
                    | --- | --- | --- |
                    | `COMMON400_1` | 400 | metadata 검증 실패 |
                    | `MEDIA400_1` | 400 | 지원하지 않는 MIME |
                    | `MEDIA400_2` | 400 | 이모티콘에 허용되지 않는 MIME |
                    | `MEDIA400_4` | 400 | 빈 파일 |
                    | `MEDIA400_5` | 400 | metadata와 file part MIME 불일치 |
                    | `MEDIA413_1` | 413 | 파일 크기 상한 초과 |
                    | `MEDIA422_1` | 422 | 선언 MIME과 실제 bytes 불일치 |
                    | `CHAT409_1` | 409 | 이모티콘 code 중복 |
                    | `MEDIA500_3` | 500 | S3 업로드 실패 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "이모티콘 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "metadata 또는 파일 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증되지 않은 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "현재 활성 관리자가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이모티콘 code 중복"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413", description = "파일 크기 상한 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "파일 bytes 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "S3 업로드 또는 조회 URL 생성 실패")
    })
    ApiResponse<ChatResDTO.AdminEmoticon> registerEmoticon(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            encoding = @Encoding(
                                    name = "metadata",
                                    contentType = MediaType.APPLICATION_JSON_VALUE
                            )
                    )
            )
            @Parameter(
                    description = "이모티콘 code, MIME, 표시 순서 metadata",
                    required = true,
                    schema = @Schema(implementation = ChatReqDTO.RegisterEmoticon.class)
            ) ChatReqDTO.RegisterEmoticon metadata,
            @Parameter(
                    description = "PNG/JPEG/WEBP 이미지 파일",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            ) MultipartFile file
    );

    @Operation(
            summary = "채팅 이모티콘 활성 상태 변경",
            description = """
                    같은 상태를 반복 요청해도 성공합니다.
                    활성화 전에는 저장된 S3 object의 key와 실제 bytes를 다시 검증합니다.
                    비활성화해도 DB metadata와 S3 object는 삭제하지 않습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "이모티콘 활성 상태 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 값 또는 저장된 media key 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증되지 않은 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "현재 활성 관리자가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "이모티콘 또는 S3 object를 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "저장된 object bytes 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "S3 object 검증 실패")
    })
    ApiResponse<Void> updateEmoticonStatus(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "상태를 변경할 이모티콘 ID", example = "1") Long emoticonId,
            ChatReqDTO.UpdateEmoticonStatus request
    );
}
