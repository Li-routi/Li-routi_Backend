package com.lirouti.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.lirouti.domain.chat.enums.ChatMessageType;

import lombok.Builder;

public final class ChatResDTO {
    private ChatResDTO() {
    }

    /**
     * 과거 메시지 조회 결과와 다음 cursor 정보를 담는 응답이다.
     */
    @Builder
    public record MessageList(
            List<Message> messages,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    /**
     * 텍스트 또는 서비스 이모티콘 하나를 표현하는 채팅 메시지 응답이다.
     */
    @Builder
    public record Message(
            Long id,
            String clientMessageId,
            Long groupId,
            Sender sender,
            ChatMessageType type,
            String content,
            Emoticon emoticon,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 메시지 발신자의 공개 식별자와 화면 표시 이름을 담는 응답이다.
     */
    @Builder
    public record Sender(
            Long memberId,
            String nickname
    ) {
    }

    /**
     * 메시지 또는 이모티콘 목록에서 사용하는 서비스 이모티콘 자산 조회 정보다.
     */
    @Builder
    public record Emoticon(
            Long id,
            String code,
            String assetUrl,
            String contentType,
            Boolean animated
    ) {
    }

    /**
     * 현재 활성 상태인 서비스 이모티콘 목록 응답이다.
     */
    @Builder
    public record EmoticonList(
            List<Emoticon> emoticons
    ) {
    }
}
