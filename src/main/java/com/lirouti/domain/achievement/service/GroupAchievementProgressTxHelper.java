package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.GroupAchievementProgress;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.repository.GroupAchievementProgressRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link GroupAchievementProgressService} 가 호출하는, 독립된 물리 트랜잭션 경계가
 * 반드시 필요한 작업들을 모아둔 헬퍼.
 *
 * <p>여기 메서드들은 모두 {@code REQUIRES_NEW} 로 실행된다. 같은 서비스 빈 안에서
 * self-invocation 하면 프록시를 거치지 않아 {@code @Transactional} 이 적용되지
 * 않으므로, 반드시 별도 빈으로 분리해야 한다.
 *
 * <p>이렇게 분리하면 이 메서드들 안에서 발생하는 예외(유니크 제약 위반 등)가
 * 호출자의 바깥 트랜잭션을 rollback-only 로 만들지 않는다. 호출자는 예외를
 * 그대로 잡아 재조회하거나 다음 회원으로 넘어가는 식으로 스스로 복구할 수 있다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroupAchievementProgressTxHelper {

    private final GroupAchievementProgressRepository groupAchievementProgressRepository;
    private final MemberAchievementRepository memberAchievementRepository;
    private final MemberRepository memberRepository;

    /**
     * 그룹 업적 진행도 행을 새로 삽입한다.
     *
     * <p>동시에 다른 트랜잭션이 먼저 삽입에 성공하면
     * {@code uk_group_achievement_progress} 유니크 제약 위반
     * ({@link org.springframework.dao.DataIntegrityViolationException})이 발생한다.
     * 이 메서드는 자신만의 트랜잭션에서 실행되므로 그 실패는 이 트랜잭션만 롤백시키고
     * 호출자의 트랜잭션에는 영향을 주지 않는다. 호출자는 예외를 잡아
     * {@code findForUpdate} 로 재조회하면 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GroupAchievementProgress createProgress(Long groupId, Achievement achievement) {
        return groupAchievementProgressRepository.saveAndFlush(
                GroupAchievementProgress.builder()
                        .groupId(groupId)
                        .achievement(achievement)
                        .build()
        );
    }

    /**
     * 회원 한 명에 대한 업적 달성 처리를 독립된 트랜잭션에서 수행한다.
     *
     * <p>한 회원 처리 중 예외가 발생해도 이 트랜잭션만 롤백되고, 다른 회원 처리나
     * 호출자(그룹 진행도 갱신)의 최종 커밋에는 영향을 주지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void achieveMemberImmediately(Long memberId, Achievement achievement) {
        MemberAchievement memberAchievement = memberAchievementRepository
                .findForUpdate(memberId, achievement.getId())
                .orElseGet(() -> memberAchievementRepository.save(
                        MemberAchievement.builder()
                                .member(memberRepository.getReferenceById(memberId))
                                .achievement(achievement)
                                .build()
                ));
        memberAchievement.achieveImmediately();
    }
}
