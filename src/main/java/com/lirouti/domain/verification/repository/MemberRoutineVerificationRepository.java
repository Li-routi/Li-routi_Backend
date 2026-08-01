package com.lirouti.domain.verification.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
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
     * 그 루틴의 인증을 최신순으로 가져온다. 커서·정렬 키는 {@code verifiedDate} 가 아니라 id 다.
     *
     * <p><b>소유권을 확인하지 않는다.</b> 조건은 {@code routineId} 하나뿐이고, 그 루틴이 요청자의
     * 것인지는 호출부가 먼저 확인한다(RoutineVerificationQueryService 의
     * {@code existsByIdAndMemberId}). 이 메서드가 걸러 준다고 믿고 소유권 확인을 건너뛰면
     * 남의 인증이 그대로 나간다.
     *
     * <p>날짜로 정렬해도 하루 1건 제약 때문에 순서는 같지만, <b>커서로는 id 가 안전하다.</b>
     * 날짜는 값이 같은 행이 생길 여지가 있고(제약이 풀리면) 그때 커서가 항목을 건너뛴다.
     * 챌린지 피드가 id 를 커서로 쓰는 것과 같은 이유다.
     *
     * <p>{@code limit} 는 요청 크기보다 하나 크게 받는다. 초과분이 오는지로 다음 페이지 유무를
     * 판단하기 위해서다(호출부의 sliceByCursor).
     */
    @Query("""
            select v from MemberRoutineVerification v
            where v.memberRoutine.id = :routineId
              and (:cursor is null or v.id < :cursor)
            order by v.id desc
            """)
    List<MemberRoutineVerification> findByRoutineIdByCursor(
            @Param("routineId") Long routineId,
            @Param("cursor") Long cursor,
            Limit limit
    );
}
