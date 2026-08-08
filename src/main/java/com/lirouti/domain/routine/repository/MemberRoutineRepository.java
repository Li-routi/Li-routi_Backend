package com.lirouti.domain.routine.repository;

import com.lirouti.domain.routine.entity.MemberRoutine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface MemberRoutineRepository extends JpaRepository<MemberRoutine, Long> {
    /** 활성 여부와 관계없이 카테고리를 참조하는 개인 루틴이 하나라도 있는지 확인한다. */
    boolean existsByCategoryId(Long categoryId);

    @Query("""
            select routine
            from MemberRoutine routine
            join fetch routine.category category
            left join category.owner categoryOwner
            left join fetch routine.template template
            where routine.member.id = :memberId
              and routine.active = true
            order by case when categoryOwner is null then 0 else 1 end asc,
                     category.displayOrder asc,
                     category.id asc,
                     case when template.id is null then 1 else 0 end asc,
                     template.displayOrder asc,
                     routine.id asc
            """)
    List<MemberRoutine> findActiveOrderedByMemberId(@Param("memberId") Long memberId);

    @Query("""
            select distinct routine
            from MemberRoutine routine
            left join fetch routine.schedules
            where routine.id in :routineIds
            """)
    List<MemberRoutine> findAllWithSchedulesByIdIn(
            @Param("routineIds") List<Long> routineIds
    );

    /**
     * 회원의 활성 개인 루틴 수를 센다. 활성 루틴 상한 검사에 사용한다.
     *
     * @param memberId 조회할 회원 ID
     * @return 활성 개인 루틴 수
     */
    long countByMemberIdAndActiveTrue(Long memberId);

    /**
     * 인증 요청이 그 회원의 활성 루틴인지 확인하며 조회한다.
     *
     * <p>소유자와 활성 여부를 조건에 넣어, 남의 루틴이나 꺼진 루틴에 인증하는 것을 막는다.
     * id 만으로 찾아 뒤에서 비교하면 응답만으로 그 id 의 존재 여부가 드러난다.
     *
     * @param id 인증 대상 루틴 ID
     * @param memberId 요청한 회원 ID
     * @return 그 회원의 활성 루틴이면 해당 루틴, 아니면 빈 값
     */
    Optional<MemberRoutine> findByIdAndMemberIdAndActiveTrue(Long id, Long memberId);

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

    /**
     * 회원의 활성 개인 루틴을 반복 요일과 함께 조회한다.
     * 리포트의 "그날 예정된 개인 루틴 수" 계산(요일별 집계)에 쓴다.
     */
    @Query("""
        select distinct routine
        from MemberRoutine routine
        left join fetch routine.schedules
        where routine.member.id = :memberId
          and routine.active = true
        """)
    List<MemberRoutine> findActiveWithSchedulesByMemberId(@Param("memberId") Long memberId);
}
