package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ACH-EG-009(자정의 방문자, 히든) 진행도. JwtAuthFilter가 인증된 요청마다 호출하고,
 * 여기서만 "지금이 자정 근처인가"를 판정해 이벤트를 발행한다.
 *
 * <p>필터는 트랜잭션이 없는 시점이라 거기서 바로 이벤트를 발행하면
 * {@code AchievementProgressService.handle()}(AFTER_COMMIT 리스너)이 진행 중인
 * 트랜잭션이 없다고 보고 이벤트를 조용히 버린다. 이 서비스를 거쳐야 트랜잭션이
 * 새로 열리고, 그 커밋 이후에만 리스너가 반응한다.
 *
 * <p>정확히 00:00:00만 허용하면 네트워크 지연·요청 처리 시간 때문에 거의 항상
 * 놓친다. 00:00:00~00:00:59 사이를 자정으로 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidnightAccessCommandService {

    private static final String CONDITION_KEY_MIDNIGHT_ACCESS = "MIDNIGHT_ACCESS";
    private static final String SOURCE_TYPE_APP_ACCESS = "APP_ACCESS";
    private static final LocalTime MIDNIGHT_WINDOW_START = LocalTime.of(0, 0, 0);
    private static final LocalTime MIDNIGHT_WINDOW_END_EXCLUSIVE = LocalTime.of(0, 1);

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 인증된 요청마다 필터가 호출한다. 자정 근처가 아니면 아무 것도 하지 않는다 —
     * 매 요청 시간 비교 자체는 저비용이라 필터에서 걸러도 문제없다.
     *
     * <p>{@code sourceId}로 오늘 날짜(epoch day)를 쓴다. 같은 날 여러 요청이 자정
     * 구간에 걸쳐 들어와도 achievement_progress_event_log의 (source_type, source_id,
     * condition_key, member_id) 유니크 제약이 하루에 한 번만 실제 반영되게 막는다.
     */
    @Transactional
    public void recordIfMidnight(Long memberId) {
        LocalDateTime now = LocalDateTime.now(TimeUtil.KST);
        LocalTime time = now.toLocalTime();
        if (time.isBefore(MIDNIGHT_WINDOW_START) || !time.isBefore(MIDNIGHT_WINDOW_END_EXCLUSIVE)) {
            return;
        }

        LocalDate today = now.toLocalDate();
        eventPublisher.publishEvent(new AchievementProgressEvent(
                memberId,
                CONDITION_KEY_MIDNIGHT_ACCESS,
                1,
                SOURCE_TYPE_APP_ACCESS,
                today.toEpochDay()
        ));
    }
}
