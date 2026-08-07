package com.lirouti.domain.chat.controller.docs;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "그룹 채팅", description = "그룹 채팅 이력·이모티콘·읽음 위치 API")
public interface ChatControllerDocs {

    @Operation(
            summary = "그룹 채팅 메시지 조회",
            description = "활성 그룹 멤버가 최신 메시지부터 cursor 방식으로 과거 메시지를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "메시지 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "그룹의 활성 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "그룹을 찾을 수 없음")
    })
    ApiResponse<ChatResDTO.MessageList> getMessages(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 그룹 ID") Long groupId,
            @Parameter(description = "이전 응답의 nextCursor") Long cursor,
            @Parameter(description = "조회할 메시지 수") Integer size
    );

    @Operation(
            summary = "채팅 이모티콘 목록 조회",
            description = "활성 회원이 사용할 수 있는 서비스 이모티콘과 조회용 asset URL을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "이모티콘 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증되지 않은 요청")
    })
    ApiResponse<ChatResDTO.EmoticonList> getEmoticons(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "그룹 채팅 읽음 위치 갱신",
            description = "회원이 읽은 마지막 메시지 ID를 저장합니다. 이전 위치로는 되돌아가지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "읽음 위치 갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "읽음 위치가 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "그룹의 활성 멤버가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "메시지를 찾을 수 없음")
    })
    ApiResponse<Void> updateReadPosition(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "읽음 위치를 갱신할 그룹 ID") Long groupId,
            ChatReqDTO.UpdateRead request
    );
}
