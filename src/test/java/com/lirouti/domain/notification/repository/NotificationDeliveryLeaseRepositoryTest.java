package com.lirouti.domain.notification.repository;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.enums.PushStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("알림 배송 lease 저장소 테스트")
class NotificationDeliveryLeaseRepositoryTest {
    @Autowired
    private NotificationRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("유효한 lease는 보호하고 만료된 SENDING 알림만 다시 선점한다")
    void claimPendingDelivery_ExpiredLease_ReclaimsWithNewTimestamp() {
        Long notificationId = persistNotification("lease-reclaim");
        LocalDateTime firstClaimedAt = LocalDateTime.of(2026, 8, 11, 12, 0);

        assertThat(claim(notificationId, firstClaimedAt)).isEqualTo(1);
        assertThat(repository.findExpiredDeliveryIds(
                PushStatus.SENDING,
                firstClaimedAt.plusMinutes(4).minusMinutes(5),
                PageRequest.of(0, 10)
        )).isEmpty();
        assertThat(claim(notificationId, firstClaimedAt.plusMinutes(4))).isZero();

        LocalDateTime reclaimedAt = firstClaimedAt.plusMinutes(6);
        assertThat(repository.findExpiredDeliveryIds(
                PushStatus.SENDING,
                reclaimedAt.minusMinutes(5),
                PageRequest.of(0, 10)
        )).containsExactly(notificationId);
        assertThat(claim(notificationId, reclaimedAt)).isEqualTo(1);

        Notification reclaimed = repository.findById(notificationId).orElseThrow();
        assertThat(reclaimed.getPushStatus()).isEqualTo(PushStatus.SENDING);
        assertThat(reclaimed.getLastPushAttemptAt()).isEqualTo(reclaimedAt);
    }

    @Test
    @DisplayName("만료된 worker는 새 lease의 배송 결과를 덮어쓰지 못한다")
    void completeClaimedDelivery_StaleLease_DoesNotOverwriteReclaimedDelivery() {
        Long notificationId = persistNotification("lease-fencing");
        LocalDateTime staleClaimedAt = LocalDateTime.of(2026, 8, 11, 12, 0);
        LocalDateTime currentClaimedAt = staleClaimedAt.plusMinutes(6);
        claim(notificationId, staleClaimedAt);
        claim(notificationId, currentClaimedAt);

        int staleCompletion = repository.completeClaimedDelivery(
                notificationId,
                PushStatus.SENDING,
                staleClaimedAt,
                PushStatus.FAILED,
                currentClaimedAt.plusSeconds(10),
                1
        );
        int currentCompletion = repository.completeClaimedDelivery(
                notificationId,
                PushStatus.SENDING,
                currentClaimedAt,
                PushStatus.SENT,
                currentClaimedAt.plusSeconds(20),
                1
        );

        Notification completed = repository.findById(notificationId).orElseThrow();
        assertThat(staleCompletion).isZero();
        assertThat(currentCompletion).isEqualTo(1);
        assertThat(completed.getPushStatus()).isEqualTo(PushStatus.SENT);
        assertThat(completed.getPushAttempts()).isEqualTo(1);
    }

    private int claim(Long notificationId, LocalDateTime claimedAt) {
        return repository.claimPendingDelivery(
                notificationId,
                PushStatus.PENDING,
                PushStatus.SENDING,
                claimedAt,
                claimedAt.minusMinutes(5)
        );
    }

    private Long persistNotification(String key) {
        Member member = Member.builder()
                .email(key + "@example.com")
                .nickname(key)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("social-" + key)
                .build();
        entityManager.persist(member);

        Notification notification = Notification.builder()
                .member(member)
                .category(NotificationCategory.PERSONAL_ROUTINE)
                .type(NotificationType.PERSONAL_ROUTINE_REMINDER)
                .title("lease test")
                .body("lease recovery")
                .referenceType("MEMBER_ROUTINE")
                .deduplicationKey(key)
                .build();
        entityManager.persist(notification);
        entityManager.flush();
        entityManager.clear();
        return notification.getId();
    }
}
