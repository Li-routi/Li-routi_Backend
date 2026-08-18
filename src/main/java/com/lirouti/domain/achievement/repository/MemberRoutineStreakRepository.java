package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberRoutineStreak;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRoutineStreakRepository extends JpaRepository<MemberRoutineStreak, Long> {

    /**
     * 개인 루틴 인증과 그룹 루틴 인증이 거의 동시에 같은 회원의 스트릭을 갱신할 수 있어
     * 비관적 락으로 순차 처리한다 (MemberAchievementRepository.findForUpdate와 같은 이유).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MemberRoutineStreak s where s.memberId = :memberId")
    Optional<MemberRoutineStreak> findByMemberIdForUpdate(@Param("memberId") Long memberId);

    /** 읽기만 하는 쪽(해금 판정 등)은 잠그지 않는다. */
    Optional<MemberRoutineStreak> findByMemberId(Long memberId);
}
