package com.lirouti.domain.notification.service;

import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 업무 이벤트에서 중복 없는 알림 행을 독립 트랜잭션으로 생성한다. */
@Service
@RequiredArgsConstructor
public class NotificationCreationService {
    private final NotificationRepository repository;

    /**
     * 새로 만든 경우에만 그 ID를 돌려주고, 이미 같은 사건 알림이 있었으면 null을 돌려준다
     * (재시도·중복 이벤트는 흡수하고 Push도 다시 보내지 않아야 하므로 호출부가 이 null로
     * 배송을 건너뛴다). DB의 충돌 무시 insert(ON DUPLICATE KEY UPDATE)로 처리하므로,
     * saveAndFlush 후 유니크 제약 예외를 catch하는 방식과 달리 flush 실패로 트랜잭션이
     * rollback-only가 되는 경로 자체가 없다. useAffectedRows=true 설정 덕분에 no-op
     * 갱신(중복)은 0건, 신규 삽입은 1건으로 구분된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(Long memberId, NotificationCategory category, NotificationType type,
                       String title, String body, Long groupId, Long referenceId,
                       String referenceType, String deduplicationKey) {
        int inserted = repository.insertOrTouch(memberId, category.name(), type.name(), title, body,
                groupId, referenceId, referenceType, deduplicationKey);
        return inserted == 1 ? repository.currentLastInsertId() : null;
    }
}
