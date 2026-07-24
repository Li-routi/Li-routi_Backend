package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRoutineRepository extends JpaRepository<GroupRoutine, Long> {
    /**
     * 대상 그룹에 동일한 제목의 루틴이 존재하는지 확인한다.
     *
     * @param groupId 대상 그룹 ID
     * @param title 확인할 루틴 제목
     * @return 동일 제목의 루틴이 존재하면 {@code true}
     */
    boolean existsByGroupIdAndTitle(Long groupId, String title);
}
