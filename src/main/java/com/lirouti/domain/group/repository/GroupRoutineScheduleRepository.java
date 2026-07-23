package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRoutineScheduleRepository extends JpaRepository<GroupRoutineSchedule, Long> {

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
