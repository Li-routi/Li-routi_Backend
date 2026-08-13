package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.repository.MemberAchievementProgressDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * "이 회원이 이 업적에 대해 오늘 날짜를 처음 채우는 것인지"를 확정 짓는 역할만 한다.
 *
 * <p>중복 판정은 repository 의 insert-ignore 로 처리한다. unique 제약 충돌을 예외로 만들지
 * 않으므로 호출한 진행도 갱신 트랜잭션을 rollback-only 로 오염시키지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MemberAchievementProgressDayService {

    private final MemberAchievementProgressDayRepository memberAchievementProgressDayRepository;

    /**
     * @return 오늘 날짜를 이 업적에 대해 처음 기록하는 것이면 true, 이미 오늘 치가
     *         기록돼 있어(unique 제약 위반) 진행도를 더 올리면 안 되면 false.
     */
    @Transactional
    public boolean tryMarkDay(MemberAchievement memberAchievement, LocalDate date) {
        return memberAchievementProgressDayRepository.insertIgnore(memberAchievement.getId(), date) == 1;
    }
}
