package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRoutineAssignmentRepository
        extends JpaRepository<GroupRoutineAssignment, Long> {
}
