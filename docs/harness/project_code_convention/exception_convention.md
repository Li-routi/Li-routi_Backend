# Exception Convention

## 1. 기본 원칙

- 예외와 성공 응답 코드는 도메인별로 관리한다.
- 메시지, HTTP 상태, 애플리케이션 코드를 Controller나 Service에 직접 작성하지 않는다.
- 비즈니스 예외는 도메인 전용 `XXXException`으로 발생시킨다.

## 2. 패키지 구조

```text
{domain}/
└── exception/
    ├── XXXException.java
    └── code/
        ├── error/
        │   └── XXXErrorCode.java
        └── success/
            └── XXXSuccessCode.java
```

예시:

```text
member/exception/
├── MemberException.java
└── code/
    ├── error/MemberErrorCode.java
    └── success/MemberSuccessCode.java
```

## 3. 도메인 Exception

- 파일명은 `XXXException`으로 작성한다.
- `exception` 패키지 최상단에 위치한다.
- 공통 `GeneralException`을 상속한다.
- 현재 도메인의 `XXXErrorCode`만 생성자로 받는다.

```java
public class MemberException extends GeneralException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode);
    }
}
```

생성자 타입을 `BaseErrorCode`로 넓혀 다른 도메인의 코드를 받게 하지 않는다.

## 4. ErrorCode

- `exception/code/error`에 위치한다.
- 파일명은 `XXXErrorCode`로 작성한다.
- `BaseErrorCode`를 구현한다.
- HTTP 상태, 메시지, 애플리케이션 코드를 가진다.

```java
@Getter
@AllArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "회원 조회에 실패하였습니다.",
        "MEMBER404_1"
    ),
    DUPLICATE_EMAIL(
        HttpStatus.CONFLICT,
        "이미 가입된 이메일입니다.",
        "MEMBER409_1"
    ),
    INVALID_SOCIAL_PROVIDER(
        HttpStatus.BAD_REQUEST,
        "해당 이메일은 다른 소셜 로그인으로 가입되어 있습니다.",
        "MEMBER400_1"
    ),
    CANNOT_RESET_SOCIAL_PASSWORD(
        HttpStatus.BAD_REQUEST,
        "소셜 로그인 사용자는 비밀번호 재설정이 불가능합니다. 해당 소셜 플랫폼에서 변경해주세요.",
        "MEMBER400_2"
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
```

## 5. SuccessCode

- `exception/code/success`에 위치한다.
- 파일명은 `XXXSuccessCode`로 작성한다.
- `BaseSuccessCode`를 구현한다.
- HTTP 상태, 메시지, 애플리케이션 코드를 가진다.
- enum 상수는 `_SUCCESS` 접미사를 사용한다.

```java
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
    );

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
```

## 6. 코드 규칙

애플리케이션 코드는 다음 형식을 따른다.

```text
{DOMAIN}{HTTP_STATUS}_{SEQUENCE}
```

예시:

```text
MEMBER200_1
MEMBER400_1
MEMBER404_1
MEMBER409_1
```

- 도메인명은 대문자로 작성한다.
- 상태 번호는 실제 `HttpStatus`와 일치해야 한다.
- 순번은 같은 도메인과 상태 코드 안에서 증가시킨다.
- 동일한 코드를 중복하거나 다른 의미로 재사용하지 않는다.
- 외부에 공개된 코드는 임의로 변경하지 않는다.

## 7. 사용 책임

- Service는 비즈니스 상황에 맞는 ErrorCode로 예외를 발생시킨다.
- Controller는 정상 응답에 맞는 SuccessCode를 선택한다.
- `GlobalExceptionHandler`가 예외를 공통 오류 응답으로 변환한다.
- Converter와 DTO는 Exception 및 Code enum에 의존하지 않는다.

```java
Member member = memberRepository.findById(memberId)
    .orElseThrow(() ->
        new MemberException(MemberErrorCode.MEMBER_NOT_FOUND)
    );
```

성공 응답은 프로젝트의 공통 응답 방식을 따른다.

```java
return ApiResponse.onSuccess(
    MemberSuccessCode.MEMBER_INFO_FETCH_SUCCESS,
    response
);
```

공통 응답 클래스나 메서드가 이미 존재하면 임의로 새 형식을 만들지 않는다.

## 8. 금지 사항

- Controller에서 도메인 예외를 직접 `try-catch`
- Service에서 `ResponseStatusException` 사용
- 모든 비즈니스 오류를 `IllegalArgumentException`으로 처리
- 메시지 또는 코드 문자열을 다른 계층에 중복 작성
- 다른 도메인의 ErrorCode를 현재 도메인 Exception에 전달
- Converter에서 Repository 조회나 비즈니스 예외 처리
- SuccessCode를 예외처럼 `throw`
- HTTP 상태와 애플리케이션 코드의 상태 번호 불일치
- 내부 구현 정보나 민감 정보를 응답 메시지에 포함
