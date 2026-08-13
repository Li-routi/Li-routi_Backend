package com.lirouti.domain.chat.entity;

import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 채팅에 저장되는 서버 기준 메시지다.
 *
 * 발신자와 생성 시각은 클라이언트 입력을 신뢰하지 않고 인증·영속화 과정에서 결정한다.
 * 재전송은 {@code (group_id, sender_id, client_message_id)} 유니크 제약과 함께 멱등 처리에 사용한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_chat_message",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_chat_message_sender_client",
                columnNames = {"group_id", "sender_id", "client_message_id"}
        )
)
public class ChatMessage extends BaseEntity {
    /** 요청 검증과 DB 컬럼 길이를 동일하게 유지하기 위한 메시지 본문 최대 길이다. */
    public static final int MAX_CONTENT_LENGTH = 2_000;

    /** 재전송 식별자가 DB 유니크 키와 API 계약에서 같은 길이 제한을 갖도록 한다. */
    public static final int MAX_CLIENT_MESSAGE_ID_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType;

    @Column(length = MAX_CONTENT_LENGTH)
    private String content;

    /** 이모티콘 메타데이터는 조회 시 조립하고, 메시지는 안정적인 외래 키만 보존한다. */
    @Column(name = "emoticon_id")
    private Long emoticonId;

    /** 답장 대상은 같은 그룹의 기존 메시지만 참조한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ChatMessage replyToMessage;

    /** 네트워크 재시도 때 동일 메시지를 다시 저장하지 않기 위한 클라이언트 생성 식별자다. */
    @Column(name = "client_message_id", nullable = false, length = MAX_CLIENT_MESSAGE_ID_LENGTH)
    private String clientMessageId;

    /**
     * 메시지 저장에 필요한 값을 구성한다.
     * 유효성 검증과 도메인 예외 변환은 ChatCommandService가 담당한다.
     */
    @Builder
    private ChatMessage(
            Group group,
            Member sender,
            ChatMessageType messageType,
            String content,
            Long emoticonId,
            ChatMessage replyToMessage,
            String clientMessageId
    ) {
        this.group = group;
        this.sender = sender;
        this.messageType = messageType;
        this.content = content;
        this.emoticonId = emoticonId;
        this.replyToMessage = replyToMessage;
        this.clientMessageId = clientMessageId;
    }

}
