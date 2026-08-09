package com.lirouti.domain.notification.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.enums.PushStatus;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Android Push와 앱 내 최근 7일 알림 목록이 함께 참조하는 영속 알림이다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification")
public class Notification extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private NotificationCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 60)
    private NotificationType type;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 255)
    private String body;
    private Long groupId;
    private Long referenceId;
    @Column(length = 40)
    private String referenceType;
    @Column(nullable = false, length = 160)
    private String deduplicationKey;
    private LocalDateTime readAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PushStatus pushStatus;
    @Column(nullable = false)
    private int pushAttempts;
    private LocalDateTime lastPushAttemptAt;

    /** 검증된 알림 내용과 중복 방지 키로 새 알림을 만든다. */
    @Builder
    private Notification(Member member, NotificationCategory category, NotificationType type,
                         String title, String body, Long groupId, Long referenceId,
                         String referenceType, String deduplicationKey) {
        this.member = member;
        this.category = category;
        this.type = type;
        this.title = title;
        this.body = body;
        this.groupId = groupId;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.deduplicationKey = deduplicationKey;
        this.pushStatus = PushStatus.PENDING;
    }

    /** 최초 읽음 시각만 기록한다. */
    public void markRead(LocalDateTime now) { if (readAt == null) readAt = now; }
    /** FCM 전송 성공을 기록한다. */
    public void markSent(LocalDateTime now) { pushAttempts++; lastPushAttemptAt = now; pushStatus = PushStatus.SENT; }
    /** 재시도 가능한 FCM 전송 실패를 기록한다. */
    public void markFailed(LocalDateTime now) { pushAttempts++; lastPushAttemptAt = now; pushStatus = PushStatus.FAILED; }
    /** 활성 기기가 없는 경우 Push를 생략한다. */
    public void markSkipped(LocalDateTime now) { lastPushAttemptAt = now; pushStatus = PushStatus.SKIPPED; }
}
