package com.lirouti.domain.routine.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.dto.request.RoutineReqDTO;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("개인 루틴 일정 교체 통합 테스트")
class RoutineScheduleReplacementTest {
    @Autowired
    private RoutineCommandService routineCommandService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("기존과 같은 요일을 포함해도 삭제가 INSERT보다 먼저 반영된다")
    void updateRoutine_KeepingRepeatDay_ReplacesSchedulesWithoutUniqueConflict() {
        Member member = Member.builder()
                .email("schedule-replacement@example.com")
                .nickname("일정교체회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("schedule-replacement-social")
                .build();
        entityManager.persist(member);
        RoutineCategory category = RoutineCategory.builder()
                .owner(member)
                .name("일정교체")
                .active(true)
                .build();
        entityManager.persist(category);
        MemberRoutine routine = MemberRoutine.builder()
                .member(member)
                .category(category)
                .name("스트레칭")
                .endTime(LocalTime.of(22, 0))
                .active(true)
                .build();
        routine.addSchedule(DayOfWeek.MONDAY);
        entityManager.persist(routine);
        entityManager.flush();

        RoutineResDTO.Routine result = routineCommandService.updateRoutine(
                member.getId(),
                routine.getId(),
                new RoutineReqDTO.UpdateRoutine(
                        "스트레칭",
                        LocalTime.of(21, 30),
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        null
                )
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(result.repeatDays())
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        MemberRoutine reloaded = entityManager.find(MemberRoutine.class, routine.getId());
        assertThat(reloaded.getSchedules())
                .extracting(schedule -> schedule.getRepeatDay())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    }
}
