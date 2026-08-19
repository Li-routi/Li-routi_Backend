package com.lirouti.domain.mypage.exception.code.error;

import com.lirouti.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuggestionErrorCode implements BaseErrorCode {

    /** 없는 분류를 보낸 경우. */
    CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 건의 분류입니다.",
            "SUGGESTION404_1"
    ),

    /**
     * 내려간 분류로 등록하려는 경우.
     *
     * <p><b>목록에서 감추는 것과 등록을 막는 것은 다르다.</b> 분류 id 를 아는 클라이언트는 목록을
     * 거치지 않고 바로 등록을 부를 수 있다 — 아이템의 판매 종료 확인과 같은 자리다.
     */
    CATEGORY_NOT_ACTIVE(
            HttpStatus.CONFLICT,
            "더 이상 사용할 수 없는 분류입니다.",
            "SUGGESTION409_1"
    ),

    /**
     * 본문이 비었거나 상한을 넘은 경우.
     *
     * <p>요청 DTO 의 {@code @NotBlank}·{@code @Size} 가 정상 경로를 막지만, <b>그 방어는
     * 컨트롤러를 거칠 때만 있다.</b> 서비스를 직접 부르는 경로가 생기면 빈 본문이 그대로
     * 저장되거나 2000자 초과가 DB 오류로 나간다.
     */
    INVALID_CONTENT(
            HttpStatus.BAD_REQUEST,
            "건의 내용은 1자 이상 2000자 이하여야 합니다.",
            "SUGGESTION400_2"
    ),

    /**
     * 제목이 비었거나 상한을 넘은 경우.
     *
     * <p>공백만 있는 제목도 여기 걸린다. 서비스가 앞뒤 공백을 뗀 뒤에 보므로 {@code "   "} 는
     * 빈 제목과 같다 — 목록에 빈 줄이 뜨는 것을 막는다.
     */
    INVALID_TITLE(
            HttpStatus.BAD_REQUEST,
            "제목은 1자 이상 100자 이하여야 합니다.",
            "SUGGESTION400_3"
    ),

    /**
     * 목록 요청의 {@code size} 가 범위를 벗어난 경우.
     *
     * <p><b>조용히 상한으로 깎지 않는다.</b> 그러면 클라이언트가 요청한 수와 받은 수가 다른
     * 이유를 알 수 없다.
     *
     * <p>파라미터 제약({@code @Min}·{@code @Max})으로 두지 않는 이유는, 이 컨트롤러가 Swagger
     * 문서 인터페이스를 구현하는데 <b>구현 쪽에서만 파라미터 제약을 더하면 Bean Validation 이
     * 거부하기 때문</b>이다({@code HV000151}). 그렇다고 문서 인터페이스에 검증을 두면 "Docs 에는
     * 문서화 어노테이션만" 이라는 규칙이 깨진다. 그래서 서비스가 검증한다.
     */
    INVALID_PAGE_SIZE(
            HttpStatus.BAD_REQUEST,
            "한 번에 받을 수 있는 개수는 1~50 입니다.",
            "SUGGESTION400_1"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
