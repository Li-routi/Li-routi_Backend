package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.RoutineCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineCategoryRepository extends JpaRepository<RoutineCategory, Long> {
    Optional<RoutineCategory> findByIdAndActiveTrue(Long id);
}
