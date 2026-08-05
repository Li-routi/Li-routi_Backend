package com.lirouti.domain.chat.entity;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원이 그룹 채팅에서 마지막으로 읽은 위치를 저장한다.
 *
 * 메시지마다 읽음 행을 만들지 않고 회원·그룹별 위치 하나만 보존해 unread count와 재연결 동기화에 사용한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_chat_read",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_chat_read_group_member",
                columnNames = {"group_id", "member_id"}
        ),
        indexes = @Index(
                name = "idx_group_chat_read_group_message",
                columnList = "group_id, last_read_message_id"
        )
)
public class ChatRead extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private ChatMessage lastReadMessage;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    private ChatRead(Group group, Member member) {
        this.group = group;
        this.member = member;
    }

    /**
     * 더 최신 메시지를 읽은 경우에만 위치를 전진시킨다.
     * 이전 메시지로 되돌아가는 요청은 무시해 여러 기기의 늦은 요청이 상태를 훼손하지 않게 한다.
     *
     * @param message 새로 읽은 마지막 메시지
     * @param readAt 읽음 처리 시각
     * @return 위치가 전진했으면 {@code true}, 기존 위치와 같거나 이전이면 {@code false}
     */
    public boolean advanceTo(ChatMessage message, LocalDateTime readAt) {
        if (lastReadMessage != null
                && lastReadMessage.getId().compareTo(message.getId()) >= 0) {
            return false;
        }
        this.lastReadMessage = message;
        this.readAt = readAt;
        return true;
    }
}
