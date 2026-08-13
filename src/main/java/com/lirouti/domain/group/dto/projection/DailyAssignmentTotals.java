package com.lirouti.domain.group.dto.projection;

/** 오늘 한 그룹의 전체 할당 수와 완료 할당 수. 0건이면 "완료할 것 자체가 없다"는 뜻이다. */
public record DailyAssignmentTotals(long totalCount, long completedCount) {
    public boolean allCompleted() {
        return totalCount > 0 && totalCount == completedCount;
    }
}
