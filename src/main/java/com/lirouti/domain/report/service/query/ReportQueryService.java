package com.lirouti.domain.report.service.query;

import com.lirouti.domain.challenge.repository.MemberChallengeRepository;
import com.lirouti.domain.group.dto.projection.DailyScheduleAndCompletion;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.report.dto.response.ReportResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.MemberRoutineSchedule;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.verification.dto.projection.DailyCompletionCount;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final MemberRoutineRepository memberRoutineRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;
    private final GroupRoutineAssignmentRepository groupRoutineAssignmentRepository;
    private final MemberChallengeRepository memberChallengeRepository;

    private static final int PLACEHOLDER_EARNED_COIN = 0; // 상점 및 코인 기능 개발 전 임시로 0

    @Transactional(readOnly = true)
    public ReportResDTO.Weekly getWeeklyReport(Long memberId, LocalDate anyDateInWeek) {
        LocalDate weekStart = startOfWeek(anyDateInWeek);
        LocalDate weekEnd = weekStart.plusDays(6);
        YearMonth displayMonth = YearMonth.from(weekStart.plusDays(3)); // 수요일이 속한 달을 기준으로 함
        int weekOfMonth = weekOfMonth(displayMonth, weekStart);

        Map<LocalDate, DayAggregate> weekAggregate = aggregate(memberId, weekStart, weekEnd);
        LocalDate today = LocalDate.now(TimeUtil.KST);

        List<ReportResDTO.DailyBar> days = weekStart.datesUntil(weekEnd.plusDays(1))
                .map(date -> {
                    DayAggregate a = weekAggregate.getOrDefault(date, DayAggregate.EMPTY);
                    return ReportResDTO.DailyBar.builder()
                            .date(date)
                            .scheduledCount(Math.toIntExact(a.scheduled()))
                            .completedCount(Math.toIntExact(a.completed()))
                            .isToday(date.equals(today))
                            .build();
                })
                .toList();

        return ReportResDTO.Weekly.builder()
                .displayMonth(displayMonth)
                .weekOfMonth(weekOfMonth)
                .weekStart(weekStart)
                .weekend(weekEnd)
                .days(days)
                .stats(computeActivityStats(memberId, displayMonth))
                .build();
    }

    @Transactional(readOnly = true)
    public ReportResDTO.Monthly getMonthlyReport(Long memberId, YearMonth yearMonth) {
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        Map<LocalDate, DayAggregate> monthAggregate = aggregate(memberId, monthStart, monthEnd);
        LocalDate today = LocalDate.now(TimeUtil.KST);

        List<ReportResDTO.DailyMark> days = monthStart.datesUntil(monthEnd.plusDays(1))
                .map(date -> {
                    DayAggregate a = monthAggregate.getOrDefault(date, DayAggregate.EMPTY);
                    return ReportResDTO.DailyMark.builder()
                            .date(date)
                            .scheduledCount(Math.toIntExact(a.scheduled()))
                            .completedCount(Math.toIntExact(a.completed()))
                            .isToday(date.equals(today))
                            .build();
                })
                .toList();

        return ReportResDTO.Monthly.builder()
                .yearMonth(yearMonth)
                .days(days)
                .stats(toActivityStats(memberId, yearMonth, monthAggregate))
                .build();
    }

    private ReportResDTO.ActivityStats computeActivityStats(Long memberId, YearMonth yearMonth) {
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        Map<LocalDate, DayAggregate> monthAggregate = aggregate(memberId, monthStart, monthEnd);
        return toActivityStats(memberId, yearMonth, monthAggregate);
    }

    private ReportResDTO.ActivityStats toActivityStats(
            Long memberId, YearMonth yearMonth, Map<LocalDate, DayAggregate> monthAggregate
    ) {
        long completedRoutineCount = monthAggregate.values().stream()
                .mapToLong(DayAggregate::completed).sum();

        double averageRate = monthAggregate.values().stream()
                .filter(day -> day.scheduled() > 0 && day.completed() > 0)
                .mapToDouble(day -> Math.min(1.0, (double) day.completed() / day.scheduled()))
                .average().orElse(0.0);
        int averageCompletionRate = (int) Math.round(averageRate * 100);

        LocalDate nextMonthStart = yearMonth.plusMonths(1).atDay(1);
        long completedChallengeCount = memberChallengeRepository
                .countByMemberIdAndActiveTrueAndJoinedAtBefore(memberId, nextMonthStart.atStartOfDay());

        return ReportResDTO.ActivityStats.builder()
                .completedRoutineCount(Math.toIntExact(completedRoutineCount))
                .averageCompletionRate(averageCompletionRate)
                .completedChallengeCount(Math.toIntExact(completedChallengeCount))
                .earnedCoinCount(PLACEHOLDER_EARNED_COIN)
                .build();
    }

    // [start, end] 구간의 날짜별 (예정 수, 완료 수)를 개인 루틴 + 그룹 루틴을 합쳐 계산
    private Map<LocalDate, DayAggregate> aggregate(Long memberId, LocalDate start, LocalDate end) {
        Map<DayOfWeek, Long> personalScheduledByWeekday = countActiveRoutinesByWeekday(
                memberRoutineRepository.findActiveWithSchedulesByMemberId(memberId));

        Map<LocalDate, Long> personalCompleted = memberRoutineVerificationRepository
                .findDailyCompletionCounts(memberId, start, end).stream()
                .collect(Collectors.toMap(DailyCompletionCount::verifiedDate, DailyCompletionCount::count));

        Map<LocalDate, DailyScheduleAndCompletion> groupByDate = groupRoutineAssignmentRepository
                .findDailyScheduleAndCompletion(memberId, start, end).stream()
                .collect(Collectors.toMap(DailyScheduleAndCompletion::date, stat -> stat));

        Map<LocalDate, DayAggregate> result = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            long personalScheduled = personalScheduledByWeekday.getOrDefault(date.getDayOfWeek(), 0L);
            long personalDone = personalCompleted.getOrDefault(date, 0L);

            DailyScheduleAndCompletion group = groupByDate.get(date);
            long groupScheduled = (group != null) ? group.scheduledCount() : 0L;
            long groupDone = (group != null) ? group.completedCount() : 0L;

            result.put(date, new DayAggregate(personalScheduled + groupScheduled, personalDone + groupDone));
        }
        return result;
    }

    private Map<DayOfWeek, Long> countActiveRoutinesByWeekday(List<MemberRoutine> routines) {
        Map<DayOfWeek, Long> counts = new EnumMap<>(DayOfWeek.class);
        for (MemberRoutine routine : routines) {
            for (MemberRoutineSchedule schedule : routine.getSchedules()) {
                counts.merge(schedule.getRepeatDay(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private LocalDate startOfWeek(LocalDate date) {
        int offset = date.getDayOfWeek().getValue() % 7; // SUNDAY(7)->0, MONDAY(1)->1, ...
        return date.minusDays(offset);
    }

    private int weekOfMonth(YearMonth displayMonth, LocalDate weekStart) {
        LocalDate firstWeekStartOfMonth = startOfWeek(displayMonth.atDay(1));
        return (int) (ChronoUnit.DAYS.between(firstWeekStartOfMonth, weekStart) / 7) + 1;
    }

    private record DayAggregate(long scheduled, long completed) {
        static final DayAggregate EMPTY = new DayAggregate(0, 0);
    }
}
