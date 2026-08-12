package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberAchievementProgressDay;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
