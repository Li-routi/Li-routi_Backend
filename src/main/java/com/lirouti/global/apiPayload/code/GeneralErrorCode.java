package com.lirouti.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST,
            "COMMON400_1",
            "잘못된 요청입니다."),
    /**
     * 토큰 없이 보호된 경로를 부른 경우. <b>토큰이 잘못된 것과 다르다</b> —
     * 그건 필터가 잡아 {@code AUTH401_*} 로 내려간다(만료·형식오류·블랙리스트).
     * 이 코드는 "아직 로그인하지 않았다"만 뜻하므로, 클라이언트는 재발급이 아니라
     * 로그인 화면으로 보내면 된다.
     */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,
            "COMMON401_1",
            "로그인이 필요합니다."),
    // 코드 문자열이 COMMON 이 아니라 AUTH 인 것은 이 파일에서 이것뿐이다. 이미 클라이언트에
    // 나간 값이라 바꾸지 않는다(exception_convention: 외부에 공개된 코드는 임의로 변경하지 않는다).
    FORBIDDEN(HttpStatus.FORBIDDEN,
            "AUTH403_1",
            "요청이 거부되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND,
            "COMMON404_1",
            "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT,
            "COMMON409_1",
            "이미 존재하는 데이터입니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT,
            "COMMON409_2",
            "동시 요청으로 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    // 사용자당 요청 빈도 제한을 넘긴 경우(#23). 언제 다시 되는지는 Retry-After 헤더로 알린다.
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS,
            "COMMON429_1",
            "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON500_1",
            "예기치 않은 서버 에러가 발생했습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
