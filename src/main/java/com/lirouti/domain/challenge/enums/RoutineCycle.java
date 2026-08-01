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
 * <p><b>쓰기 경로는 아직 이 주기를 보지 않는다.</b> 하루 1회는 DB 유니크 제약이 막고 있지만
 * 주 1회·월 1회를 막는 것은 없다 — 유니크 키가 날짜 기준이라 같은 주 다른 날 인증이 그대로
 * 들어간다. 스트릭도 일 단위로만 센다. 현재 데이터가 전부 {@code DAILY} 라 드러나지 않을 뿐이므로,
 * 주간·월간 챌린지를 실제로 넣기 전에 쓰기 차단과 스트릭 셈법을 함께 맞춰야 한다.
 */
public enum RoutineCycle {
    DAILY {
        @Override
        public LocalDate currentPeriodStart(LocalDate date) {
            return date;
        }
    },
    WEEKLY {
        @Override
        public LocalDate currentPeriodStart(LocalDate date) {
            // 그날이 일요일이면 그날 자신이 시작일이다(previousOrSame).
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        }
    },
    MONTHLY {
        @Override
        public LocalDate currentPeriodStart(LocalDate date) {
            return date.withDayOfMonth(1);
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

    /** 두 날짜가 같은 주기 구간에 속하는지. */
    public boolean isSamePeriod(LocalDate a, LocalDate b) {
        return currentPeriodStart(a).equals(currentPeriodStart(b));
    }
}
