package com.lirouti.domain.routine.repository;

import com.lirouti.domain.routine.entity.MemberRoutine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;

public interface MemberRoutineRepository extends JpaRepository<MemberRoutine, Long> {
    /**
     * 회원의 활성 개인 루틴 수를 센다. 활성 루틴 상한 검사에 사용한다.
     *
     * @param memberId 조회할 회원 ID
     * @return 활성 개인 루틴 수
     */
    long countByMemberIdAndActiveTrue(Long memberId);

    /**
     * 회원이 이미 고른 기본 제공 루틴의 ID를 조회한다. 같은 기본 루틴을 두 번 등록하려는
     * 요청을 DB 제약 위반 전에 걸러내고, 목록 화면에서 어떤 항목이 이미 체크된 상태인지
     * 알려 주는 데 쓴다.
     *
     * <p>활성 여부를 보지 않는다. {@code uk_member_routine_member_template} 제약이 활성
     * 여부와 무관하게 걸리므로, 비활성 루틴이 남아 있는 템플릿도 중복으로 취급해야 한다.
     *
     * @param memberId 조회할 회원 ID
     * @return 이미 등록된 기본 제공 루틴 ID 목록
     */
    @Query("""
            select routine.template.id
            from MemberRoutine routine
            where routine.member.id = :memberId
              and routine.template is not null
            """)
    List<Long> findTemplateIdsByMemberId(@Param("memberId") Long memberId);

    /**
     * 홈 화면 '오늘의 루틴' 탭에 쓸, 오늘 반복 요일에 해당하는 회원의 활성 개인 루틴을 조회한다.
     *
     * <p>{@code schedules}를 왼쪽 fetch join으로 통째로 가져오는 이유는, 응답이 루틴별 전체
     * 반복 요일 목록을 함께 내려 주기 때문이다({@code repeatDays}). 오늘 해당하는 루틴만
     * 고르는 조건은 {@code exists} 서브쿼리로 따로 두어, 요일 필터와 fetch join이 같은
     * 컬렉션 조인에 걸려 결과가 잘리는 문제를 피한다.
     *
     * @param memberId 조회할 회원 ID
     * @param repeatDay 오늘에 해당하는 요일
     * @return 마감 시각 순으로 정렬된 활성 개인 루틴 목록
     */
    @Query("""
            select routine
            from MemberRoutine routine
            join fetch routine.category
            left join fetch routine.template
            left join fetch routine.schedules
            where routine.member.id = :memberId
                and routine.active = true
                and exists (
                    select 1
                    from MemberRoutineSchedule schedule
                    where schedule.memberRoutine = routine
                        and schedule.repeatDay = :repeatDay
                )
                order by routine.endTime asc, routine.id asc
            """)
    List<MemberRoutine> findTodayActiveByMemberId(
            @Param("memberId") Long memberId,
            @Param("repeatDay")DayOfWeek repeatDay
    );

}
