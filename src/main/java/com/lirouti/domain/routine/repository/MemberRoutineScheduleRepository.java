package com.lirouti.domain.routine.repository;

import com.lirouti.domain.routine.entity.MemberRoutineSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;

public interface MemberRoutineScheduleRepository extends JpaRepository<MemberRoutineSchedule, Long> {

    /**
     * 개인 루틴 수정 전에 기존 반복 일정을 물리 삭제한다.
     *
     * <p>같은 요일을 다시 추가할 때 Hibernate가 INSERT를 orphan DELETE보다 먼저 실행하면
     * UNIQUE 제약이 충돌한다. 벌크 DELETE를 먼저 flush하고 영속성 컨텍스트를 비워, 호출부가
     * 루틴을 다시 조회한 뒤 새 일정만 추가하도록 한다.
     *
     * @param memberRoutineId 일정을 모두 삭제할 개인 루틴 ID
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from MemberRoutineSchedule schedule
            where schedule.memberRoutine.id = :memberRoutineId
            """)
    void deleteAllByMemberRoutineId(@Param("memberRoutineId") Long memberRoutineId);

    /**
     * 리포트 집계용. 그 회원의 <b>활성</b> 개인 루틴이 가진 반복 요일을 전부 가져온다.
     *
     * <p>개인 루틴은 그룹 루틴({@code GroupRoutineAssignment})과 달리 "그날 예정된 것"을
     * 저장하는 테이블이 없다 — 요일 반복 패턴만 있다. 그래서 요일별 개수를 한 번만 조회해
     * 서비스 계층에서 {@code DayOfWeek}로 버킷을 세운 뒤, 조회 기간의 각 날짜에 해당 요일의
     * 버킷 값을 그대로 예정 건수로 쓴다.
     *
     * <p>루틴이 기간 중 활성화·비활성화됐다면 그 변화 이력은 반영하지 못한다 — 현재 활성
     * 상태를 기준으로 계산한다는 한계가 있다(과거 리포트를 조회해도 "지금 활성 루틴" 기준).
     */
    @Query("""
            select s.repeatDay
            from MemberRoutineSchedule s
            where s.memberRoutine.member.id = :memberId
              and s.memberRoutine.active = true
            """)
    List<DayOfWeek> findActiveRepeatDaysByMemberId(@Param("memberId") Long memberId);
}
