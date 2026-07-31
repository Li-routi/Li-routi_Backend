package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupRoutineRepository extends JpaRepository<GroupRoutine, Long> {
    /** 그룹에 현재 등록된 루틴 수를 조회한다. */
    long countByGroupId(Long groupId);

    /**
     * 대상 그룹에 동일한 제목의 루틴이 존재하는지 확인한다.
     *
     * @param groupId 대상 그룹 ID
     * @param title 확인할 루틴 제목
     * @return 동일 제목의 루틴이 존재하면 {@code true}
     */
    boolean existsByGroupIdAndTitle(Long groupId, String title);

    /**
     * 수정 대상 루틴이 요청 그룹에 속하는지 함께 확인하고 해당 루틴 행을 잠근다.
     * 동일 루틴 수정 요청과 일정을 기준으로 한 할당 처리가 직렬화되도록 변경 명령에서 사용한다.
     *
     * @param routineId 수정 대상 그룹 루틴 ID
     * @param groupId 요청 대상 그룹 ID
     * @return 대상 그룹에 속하는 잠긴 그룹 루틴, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select routine
            from GroupRoutine routine
            where routine.id = :routineId
              and routine.group.id = :groupId
            """)
    Optional<GroupRoutine> findByIdAndGroupIdForUpdate(
            @Param("routineId") Long routineId,
            @Param("groupId") Long groupId
    );

    /**
     * 수정 대상 자신을 제외하고 동일 그룹에 같은 제목의 다른 루틴이 있는지 확인한다.
     *
     * @param groupId 대상 그룹 ID
     * @param title 변경할 루틴 제목
     * @param routineId 수정 대상 그룹 루틴 ID
     * @return 같은 제목의 다른 루틴이 존재하면 {@code true}
     */
    boolean existsByGroupIdAndTitleAndIdNot(Long groupId, String title, Long routineId);
}
