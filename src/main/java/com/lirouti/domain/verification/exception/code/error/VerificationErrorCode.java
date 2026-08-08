package com.lirouti.domain.verification.exception.code.error;

import org.springframework.http.HttpStatus;

import com.lirouti.global.apiPayload.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VerificationErrorCode implements BaseErrorCode {

    // 남의 루틴이거나 없는 루틴. 소유자 조건을 조회에 넣으므로 둘이 구분되지 않는다 —
    // 구분해서 알려주면 응답만으로 그 id의 존재 여부가 드러난다.
    ROUTINE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "루틴을 찾을 수 없습니다.",
            "VERIFICATION404_1"
    ),
    ASSIGNMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "오늘 수행할 그룹 루틴이 없습니다.",
            "VERIFICATION404_2"
    ),
    // 오늘이 그 루틴의 수행 요일이 아니다. 개인 루틴은 등록할 때 요일을 정하므로
    // 아무 날이나 인증할 수 없다.
    NOT_SCHEDULED_TODAY(
            HttpStatus.CONFLICT,
            "오늘은 이 루틴을 수행하는 날이 아닙니다.",
            "VERIFICATION409_1"
    ),
    // 이미 인증한 것을 다시 인증하려는 경우. 챌린지와 달리 덮어쓰기를 허용하지 않는다.
    ALREADY_VERIFIED(
            HttpStatus.CONFLICT,
            "이미 인증했습니다.",
            "VERIFICATION409_2"
    ),
    // 같은 인증 요청이 동시에 들어와 유니크 제약에 걸린 경우.
    VERIFICATION_CONFLICT(
            HttpStatus.CONFLICT,
            "인증 처리 중 중복 요청이 감지되었습니다. 잠시 후 다시 시도해 주세요.",
            "VERIFICATION409_3"
    ),
    GROUP_ROUTINE_VERIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "그룹 루틴 인증 게시물을 찾을 수 없습니다.",
            "VERIFICATION404_3"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
