package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberMorningRoutineStreak;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberMorningRoutineStreakRepository extends JpaRepository<MemberMorningRoutineStreak, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MemberMorningRoutineStreak s where s.memberId = :memberId")
    Optional<MemberMorningRoutineStreak> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
