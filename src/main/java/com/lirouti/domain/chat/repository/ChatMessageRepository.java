package com.lirouti.domain.chat.repository;

import com.lirouti.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 그룹의 최신 메시지부터 cursor 이전의 메시지를 조회한다.
     *
     * <p>정렬과 cursor를 모두 서버 ID 기준으로 처리해 같은 생성 시각의 메시지가 있어도
     * 페이지 경계가 흔들리지 않게 한다. 발신자를 fetch join해 응답 변환 시 메시지마다 회원을
     * 다시 조회하는 N+1을 피한다.
     *
     * @param groupId 조회할 그룹 ID
     * @param cursor 기준 메시지 ID. {@code null}이면 최신 메시지부터 시작한다
     * @param pageable 조회 크기. 다음 페이지 판단을 위해 호출부가 한 건 더 요청할 수 있다
     * @return ID 내림차순 메시지 목록
     */
    @Query("""
            select message
            from ChatMessage message
            join fetch message.sender
            left join fetch message.replyToMessage reply
            left join fetch reply.sender
            where message.group.id = :groupId
              and (:cursor is null or message.id < :cursor)
            order by message.id desc
            """)
    List<ChatMessage> findMessagesByGroupId(
            @Param("groupId") Long groupId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    /**
     * 날짜를 선택한 채팅 조회에 사용한다. 첫 요청은 날짜 하한을 적용하고,
     * 다음 cursor 요청은 상한만 적용해 선택 날짜 이전으로 이어서 조회할 수 있다.
     */
    @Query("""
            select message
            from ChatMessage message
            join fetch message.sender
            left join fetch message.replyToMessage reply
            left join fetch reply.sender
            where message.group.id = :groupId
              and (:cursor is null or message.id < :cursor)
              and (:fromInclusive is null or message.createdAt >= :fromInclusive)
              and message.createdAt < :toExclusive
            order by message.id desc
            """)
    List<ChatMessage> findMessagesByGroupIdAndCreatedAtRange(
            @Param("groupId") Long groupId,
            @Param("cursor") Long cursor,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );

    boolean existsByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long groupId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    );

    boolean existsByGroupIdAndCreatedAtLessThan(
            Long groupId,
            LocalDateTime createdAt
    );

    /**
     * KST 날짜 범위에 실제 메시지가 존재하는 날짜만 오름차순으로 반환한다.
     */
    @Query(value = """
            select distinct date(created_at)
            from group_chat_message
            where group_id = :groupId
              and created_at >= :fromInclusive
              and created_at < :toExclusive
            order by date(created_at)
            """, nativeQuery = true)
    List<LocalDate> findChatDatesByGroupIdAndCreatedAtRange(
            @Param("groupId") Long groupId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    /**
     * 같은 그룹의 회원이 같은 clientMessageId로 보낸 기존 메시지를 찾는다.
     * 재전송 요청이 중복 저장되지 않고 기존 서버 결과를 반환하도록 멱등 처리에 사용한다.
     *
     * @param groupId 메시지가 속한 그룹 ID
     * @param senderId 인증된 발신자 ID
     * @param clientMessageId 클라이언트가 생성한 재전송 식별자
     * @return 기존 메시지가 있으면 해당 메시지
     */
    Optional<ChatMessage> findByGroupIdAndSenderIdAndClientMessageId(
            Long groupId,
            Long senderId,
            String clientMessageId
    );

    /**
     * 그룹·발신자·clientMessageId 조합으로 메시지를 한 번만 삽입한다.
     * 동시 재전송이 들어와도 DB unique 키가 승자를 결정하도록 애플리케이션의
     * 조회 후 저장 사이 경쟁 구간을 제거한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO group_chat_message (
                created_at,
                emoticon_id,
                group_id,
                sender_id,
                reply_to_message_id,
                updated_at,
                client_message_id,
                content,
                message_type
            ) VALUES (
                CURRENT_TIMESTAMP(6),
                :emoticonId,
                :groupId,
                :senderId,
                :replyToMessageId,
                CURRENT_TIMESTAMP(6),
                :clientMessageId,
                :content,
                :messageType
            )
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("groupId") Long groupId,
            @Param("senderId") Long senderId,
            @Param("clientMessageId") String clientMessageId,
            @Param("messageType") String messageType,
            @Param("content") String content,
            @Param("emoticonId") Long emoticonId,
            @Param("replyToMessageId") Long replyToMessageId
    );

    /**
     * 답장 없는 기존 메시지 저장 호출과의 호환을 유지한다.
     */
    default int insertIfAbsent(
            Long groupId,
            Long senderId,
            String clientMessageId,
            String messageType,
            String content,
            Long emoticonId
    ) {
        return insertIfAbsent(
                groupId,
                senderId,
                clientMessageId,
                messageType,
                content,
                emoticonId,
                null
        );
    }

    /**
     * 읽음 위치 갱신 대상 메시지가 요청 그룹에 속하는지 함께 확인한다.
     * ID만 조회한 뒤 Service에서 그룹을 비교하지 않아 다른 그룹의 메시지 ID를 읽음 위치로
     * 저장하는 실수를 막는다.
     *
     * @param messageId 읽음 처리할 메시지 ID
     * @param groupId 요청 그룹 ID
     * @return 해당 그룹의 메시지면 메시지 Entity
     */
    Optional<ChatMessage> findByIdAndGroupId(Long messageId, Long groupId);
}
