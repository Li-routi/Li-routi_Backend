package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.GroupRoutineProjection;
import com.lirouti.domain.group.repository.GroupRoutineQueryRepository.RoutineScheduleProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("GroupRoutineQueryRepository QueryDSL 테스트")
class GroupRoutineQueryRepositoryTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private GroupRoutineQueryRepository groupRoutineQueryRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("ACTIVE 그룹의 ACTIVE 루틴만 생성 최신순으로 조회하고 일정을 분리 배치 조회한다")
    void findActiveRoutinesByGroupId_ReturnsFilteredRoutinesAndSchedules() {
        // given
        Group targetGroup = group();
        Group otherGroup = group();
        Group deletedGroup = group();
        GroupRoutineCategory category = category();

        GroupRoutine older = routine(targetGroup, category, "먼저 생성된 루틴");
        older.addSchedule(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));
        older.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        GroupRoutine newer = routine(targetGroup, category, "나중 생성된 루틴");
        newer.addSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(13, 0));
        GroupRoutine sameCreatedLater = routine(targetGroup, category, "같은 시각에 나중 생성된 루틴");
        GroupRoutine inactive = routine(targetGroup, category, "삭제된 루틴");
        inactive.addSchedule(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));
        inactive.delete();
        routine(otherGroup, category, "다른 그룹 루틴");
        GroupRoutine deletedGroupRoutine = routine(deletedGroup, category, "삭제 그룹 루틴");
        deletedGroupRoutine.addSchedule(DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(15, 0));
        deletedGroup.delete();

        em.flush();
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2026, 8, 1, 9, 0));
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2026, 8, 2, 9, 0));
        ReflectionTestUtils.setField(
                sameCreatedLater, "createdAt", LocalDateTime.of(2026, 8, 2, 9, 0));
        em.flush();
        Long sameCreatedLaterId = sameCreatedLater.getId();
        Long newerId = newer.getId();
        Long olderId = older.getId();
        Long inactiveId = inactive.getId();
        Long deletedGroupRoutineId = deletedGroupRoutine.getId();
        em.clear();

        // when
        List<GroupRoutineProjection> routines = groupRoutineQueryRepository
                .findActiveRoutinesByGroupId(targetGroup.getId());
        List<RoutineScheduleProjection> schedules = groupRoutineQueryRepository
                .findSchedulesByRoutineIds(List.of(
                        olderId, newerId, sameCreatedLaterId, inactiveId, deletedGroupRoutineId));

        // then
        assertThat(routines).extracting(GroupRoutineProjection::routineId)
                .containsExactly(sameCreatedLaterId, newerId, olderId);
        assertThat(routines).extracting(GroupRoutineProjection::title)
                .containsExactly("같은 시각에 나중 생성된 루틴", "나중 생성된 루틴", "먼저 생성된 루틴");
        assertThat(schedules).containsExactlyInAnyOrder(
                new RoutineScheduleProjection(
                        olderId, DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)),
                new RoutineScheduleProjection(
                        olderId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                new RoutineScheduleProjection(
                        newerId, DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(13, 0))
        );
    }

    private Group group() {
        int value = sequence.incrementAndGet();
        Group group = Group.builder()
                .name("루틴 조회 그룹" + value)
                .inviteCode(String.format("Q%06d", value))
                .build();
        em.persist(group);
        return group;
    }

    private GroupRoutineCategory category() {
        int value = sequence.incrementAndGet();
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("루틴 조회 카테고리" + value)
                .active(true)
                .build();
        em.persist(category);
        return category;
    }

    private GroupRoutine routine(Group group, GroupRoutineCategory category, String title) {
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description("루틴 조회 설명")
                .build();
        em.persist(routine);
        return routine;
    }
}
