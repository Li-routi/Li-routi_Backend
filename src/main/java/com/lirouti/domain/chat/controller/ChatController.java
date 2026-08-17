package com.lirouti.domain.chat.controller;

import java.time.LocalDate;
import java.util.List;

import com.lirouti.domain.chat.controller.docs.ChatControllerDocs;
import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.exception.code.success.ChatSuccessCode;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.domain.chat.service.query.ChatQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController implements ChatControllerDocs {
    private final ChatQueryService chatQueryService;
    private final ChatCommandService chatCommandService;

    /**
     * 그룹 채팅 이력을 cursor 기준으로 조회한다.
     */
    @Override
    @GetMapping("/api/groups/{groupId}/chat/messages")
    public ApiResponse<ChatResDTO.MessageList> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size
    ) {
        ChatResDTO.MessageList result = chatQueryService.getMessages(
                userDetails.getMemberId(), groupId, date, cursor, size);
        return ApiResponse.onSuccess(ChatSuccessCode.MESSAGE_LIST_FETCH_SUCCESS, result);
    }

    /**
     * 그룹 채팅이 존재하는 날짜를 KST 기준으로 조회한다.
     */
    @Override
    @GetMapping("/api/groups/{groupId}/chat/dates")
    public ApiResponse<ChatResDTO.ChatDates> getChatDates(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        List<LocalDate> result = chatQueryService.getChatDates(
                userDetails.getMemberId(), groupId, from, to);
        return ApiResponse.onSuccess(
                ChatSuccessCode.CHAT_DATES_FETCH_SUCCESS,
                ChatResDTO.ChatDates.builder()
                        .chatDates(result)
                        .build());
    }

    /**
     * 활성 회원이 사용할 수 있는 서비스 이모티콘 목록을 조회한다.
     */
    @Override
    @GetMapping("/api/chat/emoticons")
    public ApiResponse<ChatResDTO.EmoticonList> getEmoticons(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ChatResDTO.EmoticonList result =
                chatQueryService.getEmoticons(userDetails.getMemberId());
        return ApiResponse.onSuccess(ChatSuccessCode.EMOTICON_LIST_FETCH_SUCCESS, result);
    }

    /**
     * 그룹 채팅에서 회원의 마지막 읽은 메시지 위치를 갱신한다.
     */
    @Override
    @PatchMapping("/api/groups/{groupId}/chat/read")
    public ApiResponse<Void> updateReadPosition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @Valid @RequestBody ChatReqDTO.UpdateRead request
    ) {
        chatCommandService.updateReadPosition(
                userDetails.getMemberId(), groupId, request);
        return ApiResponse.onSuccess(ChatSuccessCode.READ_POSITION_UPDATE_SUCCESS, null);
    }
}
