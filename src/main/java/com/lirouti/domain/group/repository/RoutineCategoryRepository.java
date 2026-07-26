package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.RoutineCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineCategoryRepository extends JpaRepository<RoutineCategory, Long> {
    /**
     * 신규 루틴에 사용할 수 있는 활성 카테고리를 ID로 조회한다.
     *
     * @param id 카테고리 ID
     * @return 활성 카테고리, 없으면 빈 값
     */
    Optional<RoutineCategory> findByIdAndActiveTrue(Long id);
}
