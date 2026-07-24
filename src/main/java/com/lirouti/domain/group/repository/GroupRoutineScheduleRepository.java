package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRoutineScheduleRepository extends JpaRepository<GroupRoutineSchedule, Long> {

    /**
     * 특정 요일의 모든 일정을 루틴과 그룹까지 함께 조회한다.
     *
     * @param repeatDay 반복 요일
     * @return 해당 요일의 그룹 루틴 일정 목록
     */
    @Query("""
            select schedule
            from GroupRoutineSchedule schedule
            join fetch schedule.groupRoutine routine
            join fetch routine.group
            where schedule.repeatDay = :repeatDay
            """)
    List<GroupRoutineSchedule> findAllWithRoutineAndGroupByRepeatDay(
            @Param("repeatDay") DayOfWeek repeatDay
    );

    /**
     * 특정 그룹과 요일에 해당하는 일정을 루틴과 함께 조회한다.
     *
     * @param groupId 대상 그룹 ID
     * @param repeatDay 반복 요일
     * @return 그룹과 요일에 해당하는 일정 목록
     */
    @Query("""
            select schedule
            from GroupRoutineSchedule schedule
            join fetch schedule.groupRoutine routine
            where routine.group.id = :groupId
              and schedule.repeatDay = :repeatDay
            """)
    List<GroupRoutineSchedule> findAllWithRoutineByGroupIdAndRepeatDay(
            @Param("groupId") Long groupId,
            @Param("repeatDay") DayOfWeek repeatDay
    );
}
