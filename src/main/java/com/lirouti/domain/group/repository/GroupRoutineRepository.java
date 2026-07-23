package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRoutineRepository extends JpaRepository<GroupRoutine, Long> {
    boolean existsByGroupIdAndTitle(Long groupId, String title);
}
