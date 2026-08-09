package com.lirouti.domain.notification.repository;

import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.NotificationCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying(clearAutomatically = true)
    @Query("""
            update Notification notification set notification.readAt = :now
            where notification.member.id = :memberId and notification.readAt is null
            """)
    int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    long deleteByCreatedAtBefore(LocalDateTime threshold);
}
