package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        GroupRoutineCategory category = category();
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
        GroupRoutineCategory category = category();
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
        GroupRoutineCategory category = category();
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

    @Test
    @DisplayName("그룹별 루틴 수는 다른 그룹의 루틴을 제외한다")
    void countByGroupId_MultipleGroups_CountsTargetGroupOnly() {
        // given
        Group targetGroup = group();
        Group otherGroup = group();
        GroupRoutineCategory category = category();
        groupRoutineRepository.save(routine(targetGroup, category, "대상 루틴 1"));
        groupRoutineRepository.save(routine(targetGroup, category, "대상 루틴 2"));
        groupRoutineRepository.saveAndFlush(routine(otherGroup, category, "다른 루틴"));
        em.clear();

        // when
        long result = groupRoutineRepository.countByGroupIdAndActiveTrue(targetGroup.getId());

        // then
        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("비활성 루틴은 활성 개수와 수정 잠금 조회에서 제외한다")
    void softDeletedRoutine_ActiveQueries_ExcludeRoutine() {
        // given
        Group group = group();
        GroupRoutine routine = groupRoutineRepository.saveAndFlush(
                routine(group, category(), "삭제 루틴")
        );
        routine.delete();
        groupRoutineRepository.flush();
        em.clear();

        // when & then
        assertThat(groupRoutineRepository.countByGroupIdAndActiveTrue(group.getId())).isZero();
        assertThat(groupRoutineRepository.findByIdAndGroupIdForUpdate(
                routine.getId(), group.getId()
        )).isEmpty();
        assertThat(groupRoutineRepository.existsByIdAndGroupId(
                routine.getId(), group.getId()
        )).isTrue();
    }

    @Test
    @DisplayName("비활성 루틴의 제목도 같은 그룹에서 계속 예약된다")
    void save_TitleOfInactiveRoutine_StillViolatesUniqueConstraint() {
        // given
        Group group = group();
        GroupRoutineCategory category = category();
        GroupRoutine inactive = groupRoutineRepository.saveAndFlush(
                routine(group, category, "예약 제목")
        );
        inactive.delete();
        groupRoutineRepository.flush();

        // when & then
        assertThatThrownBy(() -> groupRoutineRepository.saveAndFlush(
                routine(group, category, "예약 제목")
        )).isInstanceOf(DataIntegrityViolationException.class);
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

    private GroupRoutineCategory category() {
        int value = sequence.incrementAndGet();
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("루틴 카테고리" + value)
                .active(true)
                .build();
        em.persist(category);
        return category;
    }

    private GroupRoutine routine(
            Group group,
            GroupRoutineCategory category,
            String title
    ) {
        return GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description("테스트 설명")
                .build();
    }
}
