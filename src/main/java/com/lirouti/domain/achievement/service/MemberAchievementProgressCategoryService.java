package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementProgressCategory;
import com.lirouti.domain.achievement.repository.MemberAchievementProgressCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * "이 회원이 이 업적에 대해 이 카테고리를 처음 커버하는 것인지"를 확정 짓는 역할만 한다.
 * {@code MemberAchievementProgressDayService} 와 같은 이유로 별도 빈 + {@code REQUIRES_NEW}.
 */
@Service
@RequiredArgsConstructor
public class MemberAchievementProgressCategoryService {

    private final MemberAchievementProgressCategoryRepository memberAchievementProgressCategoryRepository;

    /**
     * @return 이 카테고리를 처음 커버하는 것이면 true, 이미 커버돼 있어(unique 제약 위반)
     *         진행도를 더 올리면 안 되면 false.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryMarkCategory(MemberAchievement memberAchievement, Long routineCategoryId) {
        try {
            memberAchievementProgressCategoryRepository.saveAndFlush(
                    MemberAchievementProgressCategory.builder()
                            .memberAchievement(memberAchievement)
                            .routineCategoryId(routineCategoryId)
                            .build()
            );
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
