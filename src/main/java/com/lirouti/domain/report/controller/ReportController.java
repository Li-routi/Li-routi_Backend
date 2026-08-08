package com.lirouti.domain.report.controller;

import com.lirouti.domain.report.controller.docs.ReportControllerDocs;
import com.lirouti.domain.report.dto.response.ReportResDTO;
import com.lirouti.domain.report.exception.code.success.ReportSuccessCode;
import com.lirouti.domain.report.service.query.ReportQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/reports")
public class ReportController implements ReportControllerDocs {

    private final ReportQueryService reportQueryService;

    @Override
    @GetMapping("/weekly")
    public ApiResponse<ReportResDTO.Weekly> getWeeklyReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(TimeUtil.KST);
        ReportResDTO.Weekly result = reportQueryService.getWeeklyReport(userDetails.getMemberId(), targetDate);
        return ApiResponse.onSuccess(ReportSuccessCode.WEEKLY_REPORT_FETCH_SUCCESS, result);
    }

    @Override
    @GetMapping("/monthly")
    public ApiResponse<ReportResDTO.Monthly> getMonthlyReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth yearMonth
    ) {
        YearMonth targetYearMonth = (yearMonth != null) ? yearMonth : YearMonth.now(TimeUtil.KST);
        ReportResDTO.Monthly result = reportQueryService.getMonthlyReport(userDetails.getMemberId(), targetYearMonth);
        return ApiResponse.onSuccess(ReportSuccessCode.MONTHLY_REPORT_FETCH_SUCCESS, result);
    }
}
