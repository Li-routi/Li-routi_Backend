package com.lirouti.global.apiPayload.handler;

import java.sql.SQLException;
import java.util.function.Predicate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.domain.reward.dto.response.RewardResDTO;
import com.lirouti.domain.reward.exception.RewardClawbackException;
import com.lirouti.domain.shop.converter.ShopConverter;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.exception.ShopInsufficientBalanceException;
import com.lirouti.domain.shop.exception.ShopItemRejectedException;
import com.lirouti.global.apiPayload.exception.GeneralException;
import com.lirouti.global.ratelimit.RateLimitExceededException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {

    /**
     * 삭제가 재화 부족으로 거절된 경우. 실패 응답에 <b>"얼마가 모자란지" 를 함께 싣는다.</b>
     *
     * <p>{@code RewardClawbackException} 은 {@code GeneralException} 의 하위 타입인데, 스프링은
     * <b>선언 순서가 아니라 예외 계층에서 더 구체적인 처리기</b>를 고른다. 그래서 이 메서드를
     * 어디에 두든 아래 {@code handleGeneralException} 보다 먼저 선택된다.
     *
     * <p>{@code GeneralException} 은 코드만 들고 다니므로 이 값이 들어갈 자리가 없다. 메시지
     * 문자열에 숫자를 끼워 넣는 방법도 있지만, 그러면 클라이언트가 문구를 파싱하게 되고
     * 문구를 고치는 순간 깨진다.
     */
    @ExceptionHandler(RewardClawbackException.class)
    public ResponseEntity<@NonNull ApiResponse<RewardResDTO.ClawbackShortfall>> handleRewardClawback(
            RewardClawbackException e) {
        log.warn("재화가 모자라 인증 삭제를 거절했습니다. 필요={}, 보유={}", e.getRequired(), e.getBalance());
        return ResponseEntity
                .status(e.getCode().getHttpStatus())
                .body(ApiResponse.onFailure(e.getCode(), RewardResDTO.ClawbackShortfall.builder()
                        .required(e.getRequired())
                        .balance(e.getBalance())
                        .shortfall(e.shortfall())
                        .build()));
    }

    /**
     * 구매가 재화 부족으로 거절된 경우. <b>모자란 재화를 전부 싣는다.</b>
     *
     * <p>재화가 섞인 구매는 파란 보석과 주황 보석이 동시에 모자랄 수 있다. 먼저 걸린 하나만
     * 알려주면 사용자가 그것을 채우고 돌아와 <b>또 막힌다</b> — 그래서 서비스가 차감 전에 전부
     * 검사해 모아 온 것을 그대로 내보낸다.
     */
    @ExceptionHandler(ShopInsufficientBalanceException.class)
    public ResponseEntity<@NonNull ApiResponse<ShopResDTO.PurchaseShortage>> handleShopInsufficientBalance(
            ShopInsufficientBalanceException e) {
        log.warn("재화가 모자라 아이템 구매를 거절했습니다. 부족={}", e.getShortages());
        return ResponseEntity
                .status(e.getCode().getHttpStatus())
                .body(ApiResponse.onFailure(e.getCode(),
                        ShopConverter.toPurchaseShortage(e.getShortages())));
    }

    /**
     * 일부 아이템 때문에 구매 전체가 거절된 경우. <b>막힌 아이템 id 를 싣는다.</b>
     *
     * <p>사유만 알려주면 화면은 여섯 개 중 무엇을 빼야 할지 알 수 없어 전체를 지우는 수밖에
     * 없다. 사유는 응답 code 가 가른다.
     */
    @ExceptionHandler(ShopItemRejectedException.class)
    public ResponseEntity<@NonNull ApiResponse<ShopResDTO.RejectedItems>> handleShopItemRejected(
            ShopItemRejectedException e) {
        log.warn("살 수 없는 아이템이 있어 구매를 거절했습니다. code={}, itemIds={}",
                e.getCode().getCode(), e.getItemIds());
        return ResponseEntity
                .status(e.getCode().getHttpStatus())
                .body(ApiResponse.onFailure(e.getCode(),
                        ShopConverter.toRejectedItems(e.getItemIds())));
    }

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

    /**
     * 레이트 리밋 초과(#23). GeneralException 처리기로도 429가 나가지만, 여기서 잡아
     * {@code Retry-After}를 붙인다 — 언제 다시 되는지 알려주지 않으면 클라이언트가 즉시
     * 재시도하며 한도를 더 깎는다.
     *
     * 로그는 인터셉터가 정책·식별자와 함께 이미 남겼으므로 여기서 다시 남기지 않는다.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleRateLimitExceeded(
            RateLimitExceededException e) {
        return ResponseEntity
                .status(e.getCode().getHttpStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiResponse.onFailure(e.getCode()));
    }

    /** Controller 진입 전에 multipart 상한으로 거부된 요청을 미디어 413 계약으로 변환한다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e
    ) {
        log.warn("multipart 요청이 허용 용량을 초과했습니다. maxUploadSize={}",
                e.getMaxUploadSize());

        return ResponseEntity
                .status(MediaErrorCode.FILE_TOO_LARGE.getHttpStatus())
                .body(ApiResponse.onFailure(MediaErrorCode.FILE_TOO_LARGE));
    }

    /** multipart 요청에 필수 파일 part가 없을 때 공통 400 응답으로 변환한다. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleMissingServletRequestPart(
            MissingServletRequestPartException e
    ) {
        log.warn("multipart 필수 part가 누락되었습니다. partName={}", e.getRequestPartName());

        return ResponseEntity
                .status(GeneralErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.BAD_REQUEST));
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

    /**
     * 매핑된 핸들러가 없는 경로. <b>404 다.</b>
     *
     * <p>이 핸들러가 없으면 아래 {@code Exception.class} 폴백이 받아 <b>500</b> 이 나간다.
     * 스프링은 핸들러를 못 찾으면 정적 리소스를 찾아보고, 그것도 없으면
     * {@code NoResourceFoundException} 을 던지는데 그것이 그대로 "처리되지 않은 서버 오류"가
     * 되기 때문이다. {@code DefaultHandlerExceptionResolver} 가 404 로 바꿔주기는 하지만
     * {@code @ExceptionHandler} 가 먼저 실행되어 거기까지 가지 않는다.
     *
     * <p><b>주소 오타를 서버 장애로 알려주는 셈이라 진단을 막는다.</b> 실제로 클라이언트가
     * 없는 경로를 부르고 "유효한 토큰인데 서버가 500 을 낸다"고 보고한 적이 있다. 404 였다면
     * 즉시 알았을 일이다. 봇 스캐너가 긁는 요청까지 전부 500 + ERROR 로그로 쌓이는 문제도 있다.
     *
     * <p>{@code warn} 이 아니라 {@code debug} 로 남긴다 — 스캐너 트래픽이 대부분이라
     * 경고로 올리면 로그가 그것으로 덮인다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<@NonNull ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("매핑된 핸들러가 없는 경로입니다. path={}", e.getResourcePath());

        return ResponseEntity
                .status(GeneralErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.NOT_FOUND));
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
