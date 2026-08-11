package com.lirouti.domain.notification.repository;

import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.PushStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 회원 소유권과 최근 7일 범위를 강제하는 알림 저장소다. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("""
            select notification from Notification notification
            where notification.member.id = :memberId
              and notification.createdAt >= :since
              and (:category is null or notification.category = :category)
              and (:cursor is null or notification.id < :cursor)
            order by notification.id desc
            """)
    List<Notification> findPage(@Param("memberId") Long memberId,
                                @Param("category") NotificationCategory category,
                                @Param("cursor") Long cursor,
                                @Param("since") LocalDateTime since,
                                Pageable pageable);

    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 아직 처리되지 않았거나 lease가 만료된 알림 한 건을 배송 작업이 원자적으로 선점한다.
     * 동시에 여러 호출이 들어와도 한 호출만 1을 반환하며 선점 시각이 후속 완료 작업의 fencing token이 된다.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            update Notification notification
            set notification.pushStatus = :sending,
                notification.lastPushAttemptAt = :claimedAt
            where notification.id = :notificationId
              and (notification.pushStatus = :pending
                   or (notification.pushStatus = :sending
                       and notification.lastPushAttemptAt <= :expiredBefore))
            """)
    int claimPendingDelivery(
            @Param("notificationId") Long notificationId,
            @Param("pending") PushStatus pending,
            @Param("sending") PushStatus sending,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("expiredBefore") LocalDateTime expiredBefore
    );

    /** lease가 만료되어 다시 배송해야 하는 SENDING 알림 ID를 오래된 순서로 조회한다. */
    @Query("""
            select notification.id from Notification notification
            where notification.pushStatus = :sending
              and notification.lastPushAttemptAt <= :expiredBefore
            order by notification.lastPushAttemptAt asc, notification.id asc
            """)
    List<Long> findExpiredDeliveryIds(
            @Param("sending") PushStatus sending,
            @Param("expiredBefore") LocalDateTime expiredBefore,
            Pageable pageable
    );

    /** 현재 worker의 lease가 여전히 유효할 때만 배송 결과를 기록한다. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            update Notification notification
            set notification.pushStatus = :resultStatus,
                notification.pushAttempts = notification.pushAttempts + :attemptIncrement,
                notification.lastPushAttemptAt = :completedAt
            where notification.id = :notificationId
              and notification.pushStatus = :sending
              and notification.lastPushAttemptAt = :claimedAt
            """)
    int completeClaimedDelivery(
            @Param("notificationId") Long notificationId,
            @Param("sending") PushStatus sending,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("resultStatus") PushStatus resultStatus,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("attemptIncrement") int attemptIncrement
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            update Notification notification set notification.readAt = :now
            where notification.member.id = :memberId and notification.readAt is null
            """)
    int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    /**
     * 같은 사건 알림이 이미 있으면 새 행을 만들지 않고, 있는 없든 그 행의 id를 이 커넥션의
     * LAST_INSERT_ID()로 남긴다. saveAndFlush + 유니크 제약 예외를 잡는 방식은 Hibernate가
     * flush 실패 시 트랜잭션을 rollback-only로 표시해 버려, 정상적으로 catch해도 커밋 시점에
     * UnexpectedRollbackException이 나는 문제가 있었다(중복 흡수가 실패로 보임).
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification
              (member_id, category, type, title, body, group_id, reference_id, reference_type,
               deduplication_key, push_status, push_attempts, created_at, updated_at)
            VALUES (:memberId, :category, :type, :title, :body, :groupId, :referenceId, :referenceType,
                    :deduplicationKey, 'PENDING', 0, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """, nativeQuery = true)
    int insertOrTouch(@Param("memberId") Long memberId, @Param("category") String category,
                      @Param("type") String type, @Param("title") String title, @Param("body") String body,
                      @Param("groupId") Long groupId, @Param("referenceId") Long referenceId,
                      @Param("referenceType") String referenceType,
                      @Param("deduplicationKey") String deduplicationKey);

    /** 바로 앞의 insertOrTouch가 이 커넥션에 남긴 id(신규 삽입이든 중복 흡수든)를 읽는다. */
    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long currentLastInsertId();

    long deleteByCreatedAtBefore(LocalDateTime threshold);
}
