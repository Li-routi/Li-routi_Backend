package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementProgressDay;
import com.lirouti.domain.achievement.repository.MemberAchievementProgressDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * "이 회원이 이 업적에 대해 오늘 날짜를 처음 채우는 것인지"를 확정 짓는 역할만 한다.
 *
 * <p>{@code AchievementProgressEventLogService} 와 같은 이유로 별도 빈 + {@code REQUIRES_NEW}
 * 를 쓴다 — unique 제약 위반(같은 날 중복)이 나도 그 실패가 상위(진행도 갱신) 트랜잭션
 * 전체를 rollback-only 로 만들지 않게 하기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class MemberAchievementProgressDayService {

    private final MemberAchievementProgressDayRepository memberAchievementProgressDayRepository;

    /**
     * @return 오늘 날짜를 이 업적에 대해 처음 기록하는 것이면 true, 이미 오늘 치가
     *         기록돼 있어(unique 제약 위반) 진행도를 더 올리면 안 되면 false.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryMarkDay(MemberAchievement memberAchievement, LocalDate date) {
        try {
            memberAchievementProgressDayRepository.saveAndFlush(
                    MemberAchievementProgressDay.builder()
                            .memberAchievement(memberAchievement)
                            .progressDate(date)
                            .build()
            );
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
