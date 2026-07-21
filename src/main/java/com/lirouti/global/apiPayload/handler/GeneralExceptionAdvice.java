package com.lirouti.global.apiPayload.handler;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

import java.sql.SQLException;
import java.util.function.Predicate;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {
    // 커스텀 예외 처리
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleGeneralException(GeneralException e) {
        BaseErrorCode code = e.getCode();

        if (code.getHttpStatus().is5xxServerError()) {
            log.error("서버 내부 오류가 발생했습니다.", e);
        } else {
            log.warn("클라이언트 요청 처리 중 오류가 발생했습니다. 코드: {}, 메시지: {}", code.getCode(), code.getMessage());
        }

        return ResponseEntity
                .status(e.getCode().getHttpStatus())
                .body(ApiResponse.onFailure(
                        e.getCode()
                ));
    }

    // @Valid에서 검증 오류가 발생한 예외에 대한 핸들러
    @ExceptionHandler(BindException.class)
    public ResponseEntity<@NonNull ApiResponse<String>> handleValidationException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();

        String message = (fieldError != null) ? fieldError.getDefaultMessage() : "검증 오류가 발생했습니다.";
        String fieldName = (fieldError != null) ? fieldError.getField() : "알 수 없는 필드";

        log.warn("요청 값 검증에 실패했습니다. 필드: {}, 메시지: {}", fieldName, message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(
                        GeneralErrorCode.BAD_REQUEST,
                        String.format("[%s] %s", fieldName, message)
                ));
    }

    // 잘못된 인자 예외 처리 (요청 본문 파싱 실패는 아래 HttpMessageNotReadableException에서 처리)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<@NonNull ApiResponse<String>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("잘못된 요청 인자가 전달되었습니다. 메시지: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(
                        GeneralErrorCode.BAD_REQUEST,
                        e.getMessage()
                ));
    }

    // 쿼리 파라미터·경로 변수의 타입 변환 실패 — 잘못된 enum 값, 숫자여야 할 경로에 문자 등.
    // @Valid 이전 바인딩 단계에서 발생하며, 핸들러가 없으면 catch-all(500)로 빠지므로 400으로 변환한다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("요청 파라미터 타입이 올바르지 않습니다. 파라미터: {}, 값: {}", e.getName(), e.getValue());

        return ResponseEntity
                .status(GeneralErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.BAD_REQUEST));
    }

    // 요청 본문(JSON)을 해석할 수 없는 경우 — 잘못된 형식, 타입 불일치, 존재하지 않는 enum 값 등.
    // @RequestBody의 enum 역직렬화 실패는 @Valid 이전 단계에서 이 예외로 발생하므로 여기서 400으로 변환한다.
    // 파싱 오류 메시지는 내부 필드·클래스명을 노출할 수 있어 응답에 담지 않는다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 해석할 수 없습니다. 메시지: {}", e.getMessage());

        return ResponseEntity
                .status(GeneralErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.BAD_REQUEST));
    }

    // DB 락 충돌·데드락 (동시 수정 등)
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handlePessimisticLockingFailure(
            PessimisticLockingFailureException e
    ) {
        log.warn("데이터베이스 락 충돌이 발생했습니다. 메시지: {}", e.getMessage());

        return ResponseEntity
                .status(GeneralErrorCode.CONCURRENT_MODIFICATION.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.CONCURRENT_MODIFICATION));
    }

    // DB 무결성 제약 위반 (unique, FK 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        if (isDuplicateKeyViolation(e)) {
            log.warn("중복 키 제약 조건을 위반했습니다. 메시지: {}", e.getMessage());
            return ResponseEntity
                    .status(GeneralErrorCode.CONFLICT.getHttpStatus())
                    .body(ApiResponse.onFailure(GeneralErrorCode.CONFLICT));
        }

        if (isForeignKeyViolation(e)) {
            log.warn("외래 키 제약 조건을 위반했습니다. 메시지: {}", e.getMessage());
            return ResponseEntity
                    .status(GeneralErrorCode.BAD_REQUEST.getHttpStatus())
                    .body(ApiResponse.onFailure(GeneralErrorCode.BAD_REQUEST));
        }

        log.error("데이터 무결성 제약 조건 위반이 발생했습니다.", e);
        return ResponseEntity
                .status(GeneralErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.INTERNAL_SERVER_ERROR));
    }

    // 그 외의 정의되지 않은 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull ApiResponse<String>> handleUnhandledException(Exception e) {
        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        log.error("처리되지 않은 서버 내부 오류가 발생했습니다.", e);

        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(
                        code,
                        "서버 내부 오류가 발생하였습니다."
                ));
    }

    // 중복 키 제약 조건 위반 여부를 확인
    private static boolean isDuplicateKeyViolation(Throwable throwable) {
        return matchesInCauseChain(throwable, GeneralExceptionAdvice::isDuplicateKeySignal);
    }

    // 외래 키 제약 조건 위반 여부를 확인
    private static boolean isForeignKeyViolation(Throwable throwable) {
        return matchesInCauseChain(throwable, GeneralExceptionAdvice::isForeignKeySignal);
    }

    // 예외의 원인 체인을 따라가며 특정 조건을 만족하는 예외가 있는지 확인
    private static boolean matchesInCauseChain(Throwable throwable, Predicate<Throwable> matcher) {
        Throwable current = throwable;
        while (current != null) {
            if (matcher.test(current)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    // 중복 키 제약 조건 위반 신호를 감지
    private static boolean isDuplicateKeySignal(Throwable throwable) {
        if (throwable instanceof SQLException sqlException) {
            if (sqlException.getErrorCode() == 1062) {
                return true;
            }
            if ("23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }

        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("duplicate entry")
                || lowerMessage.contains("duplicate key")
                || lowerMessage.contains("unique constraint");
    }

    // 외래 키 제약 조건 위반 신호를 감지
    private static boolean isForeignKeySignal(Throwable throwable) {
        if (throwable instanceof SQLException sqlException) {
            int errorCode = sqlException.getErrorCode();
            if (errorCode == 1451 || errorCode == 1452) {
                return true;
            }
            if ("23503".equals(sqlException.getSQLState())) {
                return true;
            }
        }

        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("foreign key constraint")
                || lowerMessage.contains("cannot add or update a child row")
                || lowerMessage.contains("cannot delete or update a parent row");
    }
}
