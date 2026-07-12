package com.lirouti.domain.member.exception.code.success;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import com.lirouti.global.apiPayload.code.BaseSuccessCode;

@Getter
@AllArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {
    MEMBER_INFO_FETCH_SUCCESS(
        HttpStatus.OK,
        "회원 정보 조회에 성공했습니다.",
        "MEMBER200_1"
    ),
    MEMBER_PROFILE_UPDATE_SUCCESS(
        HttpStatus.OK,
        "프로필 수정에 성공했습니다.",
        "MEMBER200_2"
    ),
    MEMBER_WITHDRAWAL_SUCCESS(
        HttpStatus.OK,
        "회원 탈퇴에 성공했습니다.",
        "MEMBER200_3"
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
