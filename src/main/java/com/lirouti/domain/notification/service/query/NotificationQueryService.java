package com.lirouti.domain.notification.service.query;

import com.lirouti.domain.notification.dto.response.NotificationResDTO;
import com.lirouti.domain.notification.entity.Notification;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 최근 7일 알림을 ID 커서 기반으로 안정적으로 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** size+1개를 읽어 다음 페이지 존재 여부를 별도 COUNT 없이 판단한다. */
    public NotificationResDTO.Page getNotifications(Long memberId, NotificationCategory category,
                                                     Long cursor, Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_SIZE : Math.max(1, Math.min(requestedSize, MAX_SIZE));
        List<Notification> rows = notificationRepository.findPage(memberId, category, cursor,
                LocalDateTime.now(clock).minusDays(7), PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<Notification> pageRows = hasNext ? rows.subList(0, size) : rows;
        List<NotificationResDTO.Item> items = pageRows.stream().map(this::toItem).toList();
        Long nextCursor = hasNext ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new NotificationResDTO.Page(items, nextCursor, hasNext);
    }

    private NotificationResDTO.Item toItem(Notification notification) {
        return new NotificationResDTO.Item(notification.getId(), notification.getCategory(),
                notification.getType(), notification.getTitle(), notification.getBody(),
                notification.getGroupId(), notification.getReferenceId(), notification.getReferenceType(),
                notification.getReadAt() != null, notification.getCreatedAt());
    }
}
