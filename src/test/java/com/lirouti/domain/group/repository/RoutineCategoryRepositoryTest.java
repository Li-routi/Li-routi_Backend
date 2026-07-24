package com.lirouti.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lirouti.domain.group.entity.RoutineCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("RoutineCategoryRepository 테스트")
class RoutineCategoryRepositoryTest {
    @Autowired
    private RoutineCategoryRepository routineCategoryRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("활성 카테고리만 신규 루틴용으로 조회한다")
    void findByIdAndActiveTrue_OnlyActiveCategory_ReturnsActiveOne() {
        // given
        RoutineCategory active = RoutineCategory.builder().name("활성-카테고리").active(true).build();
        RoutineCategory inactive = RoutineCategory.builder().name("비활성-카테고리").active(false).build();
        em.persist(active);
        em.persist(inactive);
        em.flush();
        em.clear();

        // when
        boolean activeFound = routineCategoryRepository.findByIdAndActiveTrue(active.getId()).isPresent();
        boolean inactiveFound = routineCategoryRepository.findByIdAndActiveTrue(inactive.getId()).isPresent();

        // then
        assertThat(activeFound).isTrue();
        assertThat(inactiveFound).isFalse();
    }
}
