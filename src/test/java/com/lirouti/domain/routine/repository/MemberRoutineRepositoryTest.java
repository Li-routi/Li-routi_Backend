package com.lirouti.domain.routine.repository;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@DisplayName("MemberRoutineRepository startTime 테스트")
class MemberRoutineRepositoryTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("migration이 start_time을 nullable TIME으로 추가한다")
    void migration_AddsNullableTimeColumn() {
        var column = jdbcTemplate.queryForMap("""
                select data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'member_routine'
                  and column_name = 'start_time'
                """);

        assertThat(column.get("DATA_TYPE")).isEqualTo("time");
        assertThat(column.get("IS_NULLABLE")).isEqualTo("YES");
        assertThat(column.get("COLUMN_DEFAULT")).isNull();
    }

    @Test
    @DisplayName("startTime 미설정 행은 NULL로 저장되고 지정한 값은 재조회된다")
    void startTime_NullAndValue_ArePersistedAsExpected() {
        Member member = member();
        RoutineCategory category = category(member);
        MemberRoutine unset = routine(member, category, "미설정");
        MemberRoutine set = routine(member, category, "설정", LocalTime.of(7, 0));

        entityManager.persist(unset);
        entityManager.persist(set);
        entityManager.flush();
        Long unsetId = unset.getId();
        Long setId = set.getId();
        entityManager.clear();

        assertThat(entityManager.find(MemberRoutine.class, unsetId).getStartTime()).isNull();
        assertThat(entityManager.find(MemberRoutine.class, setId).getStartTime())
                .isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    @DisplayName("MySQL CHECK가 startTime이 endTime보다 늦거나 같은 값을 차단한다")
    void timeRangeCheck_RejectsInvalidRange() {
        Member member = member();
        RoutineCategory category = category(member);
        MemberRoutine invalid = routine(member, category, "잘못된 범위", LocalTime.of(10, 0));
        invalid.update(
                invalid.getName(),
                LocalTime.of(10, 0),
                LocalTime.of(9, 0),
                null,
                java.util.List.of(java.time.DayOfWeek.MONDAY)
        );

        assertThatThrownBy(() -> {
            entityManager.persist(invalid);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    private MemberRoutine routine(Member member, RoutineCategory category, String name) {
        return routine(member, category, name, null);
    }

    private MemberRoutine routine(
            Member member,
            RoutineCategory category,
            String name,
            LocalTime startTime
    ) {
        return MemberRoutine.builder()
                .member(member)
                .category(category)
                .name(name)
                .startTime(startTime)
                .endTime(LocalTime.of(23, 0))
                .active(true)
                .build();
    }

    private Member member() {
        String suffix = UUID.randomUUID().toString();
        Member member = Member.builder()
                .email("routine-repository-" + suffix + "@example.com")
                .nickname("루틴저장소")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("routine-repository-" + suffix)
                .build();
        entityManager.persist(member);
        return member;
    }

    private RoutineCategory category(Member member) {
        RoutineCategory category = RoutineCategory.builder()
                .owner(member)
                .name("저장소 카테고리-" + UUID.randomUUID())
                .active(true)
                .build();
        entityManager.persist(category);
        return category;
    }
}
