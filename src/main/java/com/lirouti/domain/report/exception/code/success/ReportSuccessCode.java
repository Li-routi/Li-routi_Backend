package com.lirouti.domain.report.exception.code.success;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReportSuccessCode implements BaseSuccessCode {

    WEEKLY_REPORT_FETCH_SUCCESS(
            HttpStatus.OK,
            "주간 리포트 조회에 성공했습니다.",
            "REPORT200_1"
    ),
    MONTHLY_REPORT_FETCH_SUCCESS(
            HttpStatus.OK,
            "월간 리포트 조회에 성공했습니다.",
            "REPORT200_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
