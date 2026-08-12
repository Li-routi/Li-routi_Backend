package com.lirouti.domain.chat.repository;

import com.lirouti.domain.chat.entity.ChatRead;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ChatReadRepository extends JpaRepository<ChatRead, Long> {

    /** 그룹 hard delete 전에 메시지 FK를 포함한 읽음 위치를 먼저 정리한다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from ChatRead chatRead
            where chatRead.group.id = :groupId
            """)
    int deleteAllByGroupId(@Param("groupId") Long groupId);

    /**
     * 회원·그룹별 기존 읽음 위치를 찾아 갱신하거나 새 행을 만들 수 있게 한다.
     */
    Optional<ChatRead> findByGroupIdAndMemberId(Long groupId, Long memberId);

    /**
     * 회원·그룹별 읽음 위치를 생성하거나 더 최신 메시지로만 전진시킨다.
     * 조회 후 저장하는 경쟁 구간을 제거해 동시에 들어온 요청이 읽음 위치를 역행시키지 않게 한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO group_chat_read (
                created_at,
                group_id,
                last_read_message_id,
                member_id,
                read_at,
                updated_at
            ) VALUES (
                CURRENT_TIMESTAMP(6),
                :groupId,
                :messageId,
                :memberId,
                :readAt,
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                read_at = IF(
                    last_read_message_id IS NULL OR last_read_message_id < :messageId,
                    :readAt,
                    read_at
                ),
                updated_at = IF(
                    last_read_message_id IS NULL OR last_read_message_id < :messageId,
                    CURRENT_TIMESTAMP(6),
                    updated_at
                ),
                last_read_message_id = IF(
                    last_read_message_id IS NULL OR last_read_message_id < :messageId,
                    :messageId,
                    last_read_message_id
                )
            """, nativeQuery = true)
    void upsertIfAhead(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("messageId") Long messageId,
            @Param("readAt") LocalDateTime readAt
    );
}
