package com.lirouti.domain.group.dto.projection;

import java.time.LocalDate;

public record DailyScheduleAndCompletion(
        LocalDate date,
        long scheduledCount,
        long completedCount
) { }
