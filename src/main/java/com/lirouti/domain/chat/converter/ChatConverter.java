package com.lirouti.domain.chat.converter;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.entity.ChatMessage;
import com.lirouti.domain.chat.entity.ChatRead;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.member.entity.Member;

import java.util.List;
import java.util.Map;

public final class ChatConverter {
    private ChatConverter() {
    }

    /**
     * 검증이 끝난 메시지 요청과 서버가 확인한 발신자·그룹 정보를 메시지 Entity로 변환한다.
     */
    public static ChatMessage toEntity(
            ChatReqDTO.SendMessage request,
            Group group,
            Member sender,
            Long emoticonId
    ) {
        return ChatMessage.builder()
                .group(group)
                .sender(sender)
                .messageType(request.type())
                .content(request.content())
                .emoticonId(emoticonId)
                .clientMessageId(request.clientMessageId())
                .build();
    }

    /**
     * 회원·그룹별 읽음 위치를 처음 저장할 때 사용할 Entity를 생성한다.
     */
    public static ChatRead toEntity(Group group, Member member) {
        return ChatRead.builder()
                .group(group)
                .member(member)
                .build();
    }

    /**
     * 검증과 S3 업로드가 끝난 등록 요청을 활성 상태의 정적 이모티콘 Entity로 변환한다.
     */
    public static ChatEmoticon toEntity(
            ChatReqDTO.RegisterEmoticon request,
            String assetKey,
            String contentType
    ) {
        return ChatEmoticon.builder()
                .code(request.code())
                .assetKey(assetKey)
                .contentType(contentType)
                .animated(false)
                .active(true)
                .displayOrder(request.displayOrder())
                .build();
    }

    /**
     * 메시지 Entity와 Service가 조립한 이모티콘 정보를 API 응답으로 변환한다.
     */
    public static ChatResDTO.Message toMessage(
            ChatMessage message,
            ChatResDTO.Emoticon emoticon
    ) {
        return ChatResDTO.Message.builder()
                .id(message.getId())
                .clientMessageId(message.getClientMessageId())
                .groupId(message.getGroup().getId())
                .sender(toSender(message.getSender()))
                .type(message.getMessageType())
                .content(message.getContent())
                .emoticon(emoticon)
                .createdAt(message.getCreatedAt())
                .build();
    }

    /**
     * 메시지 목록과 페이지네이션 정보를 하나의 응답으로 조립한다.
     */
    public static ChatResDTO.MessageList toMessageList(
            List<ChatMessage> messages,
            Map<Long, ChatResDTO.Emoticon> emoticons,
            Long nextCursor,
            boolean hasNext
    ) {
        List<ChatResDTO.Message> results = messages.stream()
                .map(message -> toMessage(
                        message,
                        message.getEmoticonId() == null
                                ? null
                                : emoticons.get(message.getEmoticonId())))
                .toList();
        return ChatResDTO.MessageList.builder()
                .messages(results)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    /**
     * 이모티콘 Entity와 조회 시점에 생성한 자산 URL을 목록 응답 항목으로 변환한다.
     */
    public static ChatResDTO.Emoticon toEmoticon(
            ChatEmoticon emoticon,
            String assetUrl
    ) {
        return ChatResDTO.Emoticon.builder()
                .id(emoticon.getId())
                .code(emoticon.getCode())
                .assetUrl(assetUrl)
                .contentType(emoticon.getContentType())
                .animated(emoticon.getAnimated())
                .build();
    }

    /**
     * 활성 이모티콘과 자산 URL을 선택 순서가 유지되는 목록 응답으로 조립한다.
     */
    public static ChatResDTO.EmoticonList toEmoticonList(
            List<ChatEmoticon> emoticons,
            Map<Long, String> assetUrls
    ) {
        List<ChatResDTO.Emoticon> results = emoticons.stream()
                .map(emoticon -> toEmoticon(emoticon, assetUrls.get(emoticon.getId())))
                .toList();
        return ChatResDTO.EmoticonList.builder()
                .emoticons(results)
                .build();
    }

    /**
     * private object key 대신 조회 시점에 발급한 URL을 사용하는 관리자 응답으로 변환한다.
     */
    public static ChatResDTO.AdminEmoticon toAdminEmoticon(
            ChatEmoticon emoticon,
            String assetUrl
    ) {
        return ChatResDTO.AdminEmoticon.builder()
                .id(emoticon.getId())
                .code(emoticon.getCode())
                .assetUrl(assetUrl)
                .contentType(emoticon.getContentType())
                .animated(emoticon.getAnimated())
                .active(emoticon.getActive())
                .displayOrder(emoticon.getDisplayOrder())
                .build();
    }

    /**
     * Repository가 정렬한 활성·비활성 이모티콘 순서를 유지해 관리자 목록을 조립한다.
     */
    public static ChatResDTO.AdminEmoticonList toAdminEmoticonList(
            List<ChatEmoticon> emoticons,
            Map<Long, String> assetUrls
    ) {
        List<ChatResDTO.AdminEmoticon> results = emoticons.stream()
                .map(emoticon -> toAdminEmoticon(
                        emoticon,
                        assetUrls.get(emoticon.getId())))
                .toList();
        return ChatResDTO.AdminEmoticonList.builder()
                .emoticons(results)
                .build();
    }

    /**
     * 메시지 발신 회원의 공개 식별자와 표시 이름만 응답 객체로 옮긴다.
     */
    private static ChatResDTO.Sender toSender(Member sender) {
        return ChatResDTO.Sender.builder()
                .memberId(sender.getId())
                .nickname(sender.getNickname())
                .build();
    }
}
