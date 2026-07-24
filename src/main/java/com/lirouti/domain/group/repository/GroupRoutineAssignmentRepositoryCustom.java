package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface GroupRoutineAssignmentRepositoryCustom {

    /**
     * 회원에게 특정 날짜에 할당된 활성 그룹의 루틴을 필요한 응답 컬럼만 조회한다.
     * 탈퇴하거나 강제 퇴장된 그룹의 기존 할당은 제외한다.
     *
     * @param memberId 할당 대상 회원 ID
     * @param assignedDate 조회할 할당 날짜
     * @return 시작 시각과 할당 ID 순으로 정렬된 조회 Projection 목록
     */
    List<TodayAssignmentProjection> findTodayAssignmentsByMemberId(
            Long memberId,
            LocalDate assignedDate
    );

    /** 오늘의 그룹 루틴 목록 조회에 필요한 컬럼만 담는 읽기 전용 Projection이다. */
    record TodayAssignmentProjection(
            Long assignmentId,
            Long routineId,
            Long groupId,
            String groupName,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            LocalDate assignedDate,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            GroupRoutineAssignmentStatus status
    ) {
    }
}
