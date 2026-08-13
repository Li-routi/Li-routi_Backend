package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberAchievementProgressDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MemberAchievementProgressDayRepository
        extends JpaRepository<MemberAchievementProgressDay, Long> {

    /** DISTINCT_DAY_COUNT(평생 누적)용 - 이 업적에 대해 회원이 쌓은 서로 다른 날짜 총 개수. */
    long countByMemberAchievementId(Long memberAchievementId);

    /**
     * WEEKLY_DISTINCT_DAY_COUNT·MONTHLY_DISTINCT_DAY_COUNT 용 - 주어진 기간(양 끝 포함) 안에
     * 속하는 날짜 개수만 센다. 경계를 벗어난 과거 기록은 자연히 제외되므로 별도 리셋 로직이
     * 필요 없다.
     */
    long countByMemberAchievementIdAndProgressDateBetween(
            Long memberAchievementId, LocalDate start, LocalDate end);

    /**
     * 오늘 날짜를 이 업적에 대해 처음 기록하는 삽입이면 실제로 INSERT 되어 1을, 이미
     * 같은 (member_achievement_id, progress_date) 조합이 있으면 unique 제약에 걸려 조용히
     * 무시되고 0을 반환한다.
     *
     * <p>{@code save()} + {@code DataIntegrityViolationException} catch 대신 이 방식을 쓰는
     * 이유: 예외 기반 중복 판정은 Hibernate 가 현재 트랜잭션을 rollback-only 로 마킹해버려서,
     * 별도의 {@code REQUIRES_NEW} 트랜잭션으로 격리해야만 했다. 그런데 그 격리가 오히려
     * (a) 상위 트랜잭션의 REPEATABLE READ 스냅샷이 이 커밋을 못 보고 집계에서 누락시키는
     * 문제와, (b) 아직 커밋 전인 상위 트랜잭션의 {@code MemberAchievement} 를 FK 체크가
     * 기다리며 생기는 락 대기 문제를 유발했다. {@code INSERT IGNORE} 는 예외를 던지지
     * 않으므로 상위(handle) 트랜잭션 안에서 그대로 실행할 수 있고, 두 문제가 모두 사라진다.
     *
     * @return 삽입된 행 수 (1 = 새 날짜, 0 = 이미 기록된 날짜)
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value = "INSERT IGNORE INTO member_achievement_progress_day "
                    + "(member_achievement_id, progress_date) VALUES (:memberAchievementId, :progressDate)",
            nativeQuery = true
    )
    int insertIgnore(
            @Param("memberAchievementId") Long memberAchievementId,
            @Param("progressDate") LocalDate progressDate
    );
}
