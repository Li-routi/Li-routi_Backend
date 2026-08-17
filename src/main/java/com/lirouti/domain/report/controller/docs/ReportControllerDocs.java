package com.lirouti.domain.report.controller.docs;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.lirouti.domain.report.dto.response.ReportResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.YearMonth;

@Tag(name = "마이 - 리포트", description = "마이페이지 리포트(주간/월간 활동 통계) 화면 API")
public interface ReportControllerDocs {

    @Operation(
            summary = "주간 리포트 조회",
            description = """
                    date가 속한 주(일~토 7일)의 일별 예정/완료 건수(개인 루틴 + 그룹 루틴)와,
                    그 주가 표시되는 달 기준의 활동 통계를 함께 내려줍니다.

                    date를 생략하면 오늘(KST)이 속한 주를 기준으로 조회합니다.
                    """
    )
    ApiResponse<ReportResDTO.Weekly> getWeeklyReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 주에 속한 아무 날짜(yyyy-MM-dd). 생략 시 오늘(KST)") LocalDate date
    );

    @Operation(
            summary = "월간 리포트 조회",
            description = """
                    해당 월의 일별 예정/완료 건수(개인 루틴 + 그룹 루틴)와 활동 통계를 내려줍니다.

                    yearMonth를 생략하면 이번 달(KST)을 기준으로 조회합니다.
                    """
    )
    ApiResponse<ReportResDTO.Monthly> getMonthlyReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회할 년월(yyyy-MM). 생략 시 이번 달(KST)") YearMonth yearMonth
    );
}
