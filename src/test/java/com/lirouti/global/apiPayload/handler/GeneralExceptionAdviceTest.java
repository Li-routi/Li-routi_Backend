package com.lirouti.global.apiPayload.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;

@DisplayName("GeneralExceptionAdvice 테스트")
class GeneralExceptionAdviceTest {

    private final GeneralExceptionAdvice advice = new GeneralExceptionAdvice();

    @Test
    @DisplayName("요청 본문 파싱 실패(HttpMessageNotReadableException)는 500이 아닌 400으로 변환한다")
    void handleHttpMessageNotReadable_Returns400() {
        // given — 잘못된 enum 값 등으로 @RequestBody 역직렬화가 실패한 상황
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("JSON parse error: invalid enum value");

        // when
        ResponseEntity<ApiResponse<Void>> response = advice.handleHttpMessageNotReadable(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getIsSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(GeneralErrorCode.BAD_REQUEST.getCode());
        // 파싱 오류 원문을 응답에 노출하지 않는다.
        assertThat(response.getBody().getResult()).isNull();
    }

    @Test
    @DisplayName("쿼리·경로 파라미터 타입 변환 실패(MethodArgumentTypeMismatch)는 400으로 변환한다")
    void handleTypeMismatch_Returns400() {
        // given — 잘못된 enum 쿼리 파라미터 등으로 바인딩이 실패한 상황
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("sort");
        when(exception.getValue()).thenReturn("INVALID");

        // when
        ResponseEntity<ApiResponse<Void>> response = advice.handleTypeMismatch(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getIsSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(GeneralErrorCode.BAD_REQUEST.getCode());
    }
}
