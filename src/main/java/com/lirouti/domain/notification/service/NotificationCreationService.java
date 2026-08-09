package com.lirouti.domain.notification.service;

import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 업무 이벤트에서 중복 없는 알림 행을 독립 트랜잭션으로 생성한다. */
@Service
@RequiredArgsConstructor
public class NotificationCreationService {
    private final NotificationRepository repository;
    private final MemberRepository memberRepository;

    /** 이미 같은 사건 알림이 있으면 기존 ID를 돌려 재시도·중복 이벤트를 흡수한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(Long memberId, NotificationCategory category, NotificationType type,
                       String title, String body, Long groupId, Long referenceId,
                       String referenceType, String deduplicationKey) {
        try {
            return repository.saveAndFlush(Notification.builder()
                    .member(memberRepository.getReferenceById(memberId)).category(category).type(type)
                    .title(title).body(body).groupId(groupId).referenceId(referenceId)
                    .referenceType(referenceType).deduplicationKey(deduplicationKey).build()).getId();
        } catch (DataIntegrityViolationException duplicate) {
            return null;
        }
    }
}
