package com.lirouti.domain.verification.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lirouti.domain.verification.entity.MemberRoutineVerification;

public interface MemberRoutineVerificationRepository
        extends JpaRepository<MemberRoutineVerification, Long> {

    Optional<MemberRoutineVerification> findByMemberRoutineIdAndVerifiedDate(
            Long memberRoutineId,
            LocalDate verifiedDate
    );

    /**
     * 그날 인증이 있는 루틴 id 만 골라온다. 루틴 목록에 "오늘 완료" 표시를 붙이는 데 쓴다.
     *
     * 루틴마다 따로 물으면 목록 크기만큼 쿼리가 나간다(N+1). 한 번에 가져와 화면에서 맞춘다.
     */
    @Query("""
            select v.memberRoutine.id
            from MemberRoutineVerification v
            where v.verifiedDate = :verifiedDate
              and v.memberRoutine.id in :routineIds
            """)
    List<Long> findVerifiedRoutineIds(
            @Param("routineIds") Collection<Long> routineIds,
            @Param("verifiedDate") LocalDate verifiedDate
    );

}
