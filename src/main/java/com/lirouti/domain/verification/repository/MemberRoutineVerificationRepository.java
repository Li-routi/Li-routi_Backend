package com.lirouti.domain.verification.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.lirouti.domain.verification.dto.projection.DailyCompletionCount;
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

    /**
     * 리포트 집계용. 기간 내 회원의 개인 루틴 인증을 날짜별 건수로 묶어 가져온다.
     * 날짜가 없는 날은 결과에 아예 나오지 않는다 — 서비스 계층에서 0으로 채워야 한다.
     */
    @Query("""
            select new com.lirouti.domain.verification.dto.projection.DailyCompletionCount(
                v.verifiedDate, count(v)
            )
            from MemberRoutineVerification v
            where v.memberRoutine.member.id = :memberId
              and v.verifiedDate between :start and :end
            group by v.verifiedDate
            """)
    List<DailyCompletionCount> findDailyCompletionCounts(
            @Param("memberId") Long memberId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * 마이 > 내인증 화면용. 그 회원이 특정 날짜에 남긴 개인 루틴 인증 전부를 가져온다.
     * 화면에 카테고리·루틴 이름을 함께 써야 해 루틴과 카테고리를 fetch join한다.
     */
    @Query("""
            select v from MemberRoutineVerification v
            join fetch v.memberRoutine mr
            join fetch mr.category
            where mr.member.id = :memberId
              and v.verifiedDate = :date
            order by v.verifiedAt desc
            """)
    List<MemberRoutineVerification> findByMemberAndDate(
            @Param("memberId") Long memberId,
            @Param("date") LocalDate date
    );
}
