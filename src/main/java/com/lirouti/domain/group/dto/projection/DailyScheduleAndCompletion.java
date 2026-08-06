package com.lirouti.domain.group.dto.projection;

import java.time.LocalDate;

public record DailyScheduleAndCompletion(
        LocalDate date,
        Long scheduledCount,
        Long completedCount
) { }
