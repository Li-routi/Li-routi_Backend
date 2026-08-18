package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberWaveRoutineStreak;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberWaveRoutineStreakRepository extends JpaRepository<MemberWaveRoutineStreak, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MemberWaveRoutineStreak s where s.memberId = :memberId")
    Optional<MemberWaveRoutineStreak> findByMemberIdForUpdate(@Param("memberId") Long memberId);

    Optional<MemberWaveRoutineStreak> findByMemberId(Long memberId);
}
