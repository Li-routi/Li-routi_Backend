package com.lirouti.domain.chat.dto.result;

import com.lirouti.domain.chat.dto.response.ChatResDTO;

import lombok.Builder;

/** 메시지 응답과 신규 저장 여부를 함께 전달하는 내부 command 결과다. */
@Builder
public record ChatSendResult(
        ChatResDTO.Message message,
        boolean newlyCreated
) {
}
