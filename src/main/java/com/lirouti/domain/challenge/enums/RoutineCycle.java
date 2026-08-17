package com.lirouti.domain.challenge.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 챌린지 인증 주기. 프론트가 매일/매주/매월로 변환해 카드 배지로 표시한다.
 *
 * <p>이 enum 이 <b>"현재 구간"의 시작일</b>을 계산한다. 상세 화면이 "이번 구간에 이미 인증했는지"를
 * 판정하는 기준이고, 그 판정으로 인증하기 버튼의 활성 여부가 갈린다.
 *
 * <p><b>주는 일요일에 시작한다(일~토).</b> 기획 확정 사항이며 ISO 표준(월요일 시작)이 아니다.
 * {@code WeekFields.ISO} 나 {@code DayOfWeek} 의 기본 순서를 그대로 쓰면 <b>일요일 인증만 지난
 * 주로 계산되어</b> 요일 하나에서만 틀리는 버그가 된다. 아래 {@code previousOrSame(SUNDAY)} 가
 * 그 보정이고, 일·토 경계는 테스트로 고정해 둔다.
 *
 * <p><b>쓰기 경로와 스트릭이 모두 이 주기를 따른다.</b> 인증 행은 구간 첫날을
 * {@code period_start_date} 에 못 박고 그 컬럼이 유니크 키에 들어가, "주기 1회"를 DB 가
 * 보장한다. 스트릭도 {@code isPreviousPeriod} 로 직전 구간을 따져 센다.
 *
 * <p>그래서 <b>이 enum 이 틀리면 조회·저장·스트릭이 한꺼번에 틀린다.</b> 경계는 테스트로
 * 날짜까지 못 박아 두었다.
 */
public enum RoutineCycle {
    DAILY {
        @Override
        public LocalDate currentPeriodStart(LocalDate date) {
            return date;
        }

        @Override
        public LocalDate previousPeriodStart(LocalDate date) {
            return date.minusDays(1);
        }
    },
    WEEKLY {
        @Override
        public LocalDate currentPeriodStart(LocalDate date) {
            // 그날이 일요일이면 그날 자신이 시작일이다(previousOrSame).
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        }

        @Override
        public LocalDate previousPeriodStart(LocalDate date) {
            return currentPeriodStart(date).minusWeeks(1);
        }
    },
    MONTHLY {
        @Override
        public LocalDate currentPeriodStart(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        public LocalDate previousPeriodStart(LocalDate date) {
            // 달마다 길이가 달라 minusDays 로는 안 된다. 1일로 맞춘 뒤 한 달을 뺀다.
            return currentPeriodStart(date).minusMonths(1);
        }
    };

    /**
     * 그 날짜가 속한 주기 구간의 첫날.
     *
     * <p>구간의 끝을 따로 계산하지 않는 이유는 시작일만으로 판정이 끝나기 때문이다 —
     * 끝까지 두면 경계 계산이 두 벌이 되어 한쪽만 틀어질 수 있다.
     *
     * @param date 기준 날짜(KST)
     * @return 그 구간의 첫날
     */
    public abstract LocalDate currentPeriodStart(LocalDate date);

    /**
     * 그 날짜가 속한 구간의 <b>바로 앞 구간</b>의 첫날. 스트릭이 이 값을 쓴다 —
     * "직전 구간에 인증했는가"가 곧 "연속인가"다.
     *
     * <p><b>{@code minusDays} 로 일반화할 수 없다.</b> 주는 7일이지만 달은 28~31일로 들쭉날쭉해
     * 주기마다 셈법이 다르다. 그래서 각 상수가 직접 구현한다.
     *
     * @param date 기준 날짜(KST)
     * @return 직전 구간의 첫날
     */
    public abstract LocalDate previousPeriodStart(LocalDate date);

    /** 두 날짜가 같은 주기 구간에 속하는지. */
    public boolean isSamePeriod(LocalDate a, LocalDate b) {
        return currentPeriodStart(a).equals(currentPeriodStart(b));
    }

    /**
     * {@code earlier} 가 {@code later} 의 <b>직전 구간</b>에 속하는지. 스트릭이 이어지는 조건이다.
     *
     * <p>같은 구간이면 {@code false} 다 — 이미 인증한 구간에 또 인증해도 연속이 늘지 않는다.
     */
    public boolean isPreviousPeriod(LocalDate earlier, LocalDate later) {
        return currentPeriodStart(earlier).equals(previousPeriodStart(later));
    }
}
