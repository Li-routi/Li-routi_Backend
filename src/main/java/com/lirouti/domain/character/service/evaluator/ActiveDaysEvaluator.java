package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.activity.repository.MemberActivityDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 활동한 날 수. 종류를 가리지 않는다 — 개인·그룹·챌린지가 모두 활동일을 만든다. */
@Component
@RequiredArgsConstructor
public class ActiveDaysEvaluator implements UnlockConditionEvaluator {

    private final MemberActivityDayRepository memberActivityDayRepository;

    @Override
    public String conditionKey() {
        return "ACTIVE_DAYS";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        return memberActivityDayRepository.countByMemberId(memberId);
    }
}
