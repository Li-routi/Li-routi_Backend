package com.lirouti.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.routine.entity.RoutineCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("GroupRoutineRepository 매핑 및 제약 테스트")
class GroupRoutineRepositoryTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private GroupRoutineRepository groupRoutineRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("그룹 루틴과 요일별 일정을 함께 저장하고 조회한다")
    void save_RoutineWithSchedules_PersistsRelationships() {
        // given
        Group group = group();
        RoutineCategory category = category();
        GroupRoutine routine = routine(group, category, "공동 정리");
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        routine.addSchedule(DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(21, 0));

        // when
        Long id = groupRoutineRepository.saveAndFlush(routine).getId();
        em.clear();
        GroupRoutine found = groupRoutineRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getGroup().getId()).isEqualTo(group.getId());
        assertThat(found.getCategory().getId()).isEqualTo(category.getId());
        assertThat(found.getSchedules())
                .extracting(GroupRoutineSchedule::getRepeatDay)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("같은 그룹에 동일한 제목의 루틴을 저장할 수 없다")
    void save_DuplicateGroupAndTitle_ThrowsDataIntegrityViolation() {
        // given
        Group group = group();
        RoutineCategory category = category();
        groupRoutineRepository.saveAndFlush(routine(group, category, "중복 제목"));

        // when & then
        assertThatThrownBy(() -> groupRoutineRepository
                .saveAndFlush(routine(group, category, "중복 제목")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 루틴에 동일한 요일 일정 두 건을 저장할 수 없다")
    void save_DuplicateScheduleDay_ThrowsDataIntegrityViolation() {
        // given
        GroupRoutine routine = routine(group(), category(), "요일 중복");
        routine.addSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        routine.getSchedules().add(GroupRoutineSchedule.builder()
                .groupRoutine(routine)
                .repeatDay(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 0))
                .build());

        // when & then
        assertThatThrownBy(() -> groupRoutineRepository.saveAndFlush(routine))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB에서도 시작 시간이 종료 시간보다 빨라야 한다")
    void save_InvalidTimeRange_ThrowsDataIntegrityViolation() {
        // given
        GroupRoutine routine = routine(group(), category(), "시간 제약");
        routine.addSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        ReflectionTestUtils.setField(
                routine.getSchedules().getFirst(),
                "endTime",
                LocalTime.of(8, 0)
        );

        // when & then
        assertThatThrownBy(() -> groupRoutineRepository.saveAndFlush(routine))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("수정 대상 루틴은 요청 그룹 범위에서만 잠금 조회한다")
    void findByIdAndGroupIdForUpdate_RoutineScope_ReturnsOnlyMatchingGroup() {
        // given
        Group targetGroup = group();
        Group otherGroup = group();
        GroupRoutine routine = groupRoutineRepository.saveAndFlush(
                routine(targetGroup, category(), "잠금 조회")
        );
        em.clear();

        // when
        GroupRoutine found = groupRoutineRepository
                .findByIdAndGroupIdForUpdate(routine.getId(), targetGroup.getId())
                .orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(routine.getId());
        assertThat(groupRoutineRepository.findByIdAndGroupIdForUpdate(
                routine.getId(), otherGroup.getId()
        )).isEmpty();
    }

    @Test
    @DisplayName("수정 제목 중복 검사는 자기 자신을 제외한다")
    void existsByGroupIdAndTitleAndIdNot_ExcludesTargetRoutine() {
        // given
        Group group = group();
        RoutineCategory category = category();
        GroupRoutine target = groupRoutineRepository.saveAndFlush(
                routine(group, category, "유지 제목")
        );
        GroupRoutine other = groupRoutineRepository.saveAndFlush(
                routine(group, category, "다른 제목")
        );

        // when & then
        assertThat(groupRoutineRepository.existsByGroupIdAndTitleAndIdNot(
                group.getId(), "유지 제목", target.getId()
        )).isFalse();
        assertThat(groupRoutineRepository.existsByGroupIdAndTitleAndIdNot(
                group.getId(), "유지 제목", other.getId()
        )).isTrue();
    }

    private Group group() {
        int value = sequence.incrementAndGet();
        Group group = Group.builder()
                .name("루틴 그룹" + value)
                .inviteCode(String.format("R%06d", value))
                .build();
        em.persist(group);
        return group;
    }

    private RoutineCategory category() {
        int value = sequence.incrementAndGet();
        RoutineCategory category = RoutineCategory.builder()
                .name("루틴 카테고리" + value)
                .active(true)
                .build();
        em.persist(category);
        return category;
    }

    private GroupRoutine routine(Group group, RoutineCategory category, String title) {
        return GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description("테스트 설명")
                .build();
    }
}
