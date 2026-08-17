# Controller Convention

## 목적
Controller는 HTTP 요청을 검증하고 Service를 호출한다.
비즈니스 로직과 변환은 다른 계층에 위임하며,
정상 응답은 공통 응답 객체 `ApiResponse<T>`로 반환한다.

## 패키지 구조
```text
{domain}/controller/
├── {Domain}Controller.java
└── docs/{Domain}ControllerDocs.java
```

## 기본 규칙
- 실제 Controller는 `{Domain}Controller`로 작성한다.
- Swagger 문서 인터페이스는 `{Domain}ControllerDocs`로 작성한다.
- 실제 Controller는 Docs 인터페이스를 구현한다.
- `@RestController`, `@RequestMapping`, 생성자 주입을 사용한다.
- Controller는 요청 처리와 응답 생성만 담당한다.

## 책임
Controller가 담당하는 작업:
- HTTP 요청 매핑
- PathVariable, RequestParam, RequestBody 수신
- Request DTO validation
- 인증 사용자 정보 추출
- Service 호출
- SuccessCode를 사용한 `ApiResponse<T>` 생성

Controller가 담당하지 않는 작업:
- Repository 직접 호출
- Entity 조회, 생성, 수정
- DTO 변환
- 비즈니스 조건 판단
- 트랜잭션 처리
- 도메인 예외 직접 처리

## Service 호출
- GET 요청은 기본적으로 `{Domain}QueryService`를 호출한다.
- POST, PUT, PATCH, DELETE 요청은 기본적으로 `{Domain}CommandService`를 호출한다.
- CQRS를 적용하지 않는 도메인은 `{Domain}Service`를 호출할 수 있으며, 선택 기준은 `service_convention.md`를 따른다.
- Service 결과를 추가 가공하지 않고 공통 응답으로 감싼다.

## 공통 응답
- 모든 정상 응답은 `ApiResponse<T>`를 반환한다.
- `ResponseEntity`를 사용하지 않는다.
- 도메인의 `XXXSuccessCode`를 사용한다.
- 메시지와 응답 코드를 문자열로 직접 작성하지 않는다.

```java
return ApiResponse.onSuccess(
    MemberSuccessCode.MEMBER_INFO_FETCH_SUCCESS,
    result
);
```

결과가 없는 성공 응답도 공통 응답 형식을 유지한다.

```java
return ApiResponse.onSuccess(
    MemberSuccessCode.MEMBER_WITHDRAWAL_SUCCESS,
    null
);
```

결과 없는 응답의 generic 타입은 프로젝트의 기존 방식을 따른다.

## 오류 응답
- Controller는 `ApiResponse.onFailure`를 직접 호출하지 않는다.
- 비즈니스 예외를 `try-catch`하지 않는다.
- ErrorCode와 오류 메시지를 직접 선택하지 않는다.
- `GlobalExceptionHandler`가 예외를 공통 실패 응답으로 변환한다.

## Validation
- RequestBody DTO에는 `@Valid`를 사용한다.
- PathVariable과 RequestParam 검증이 필요하면 `@Validated`를 사용한다.
- 형식과 단순 제약은 Request DTO에서 검증한다.
- DB 조회가 필요한 검증과 비즈니스 규칙은 Service에서 처리한다.
- `@Valid`와 `@Validated`는 실제 Controller 메서드 파라미터에 작성하고, Docs 인터페이스에는 문서화 어노테이션만 둔다.
- HTTP 요청의 필수 입력 null 방어는 Controller의 validation 단계에서 처리한다.

## Docs 인터페이스
Docs 인터페이스에는 Swagger/OpenAPI 문서화만 작성한다.

- `@Tag`
- `@Operation`
- `@ApiResponses`
- `@Parameter`
- 요청 및 응답 스키마 설명

Docs와 실제 Controller의 메서드 이름, 매개변수,
반환 타입은 일치해야 한다.

```java
@Tag(name = "Member", description = "회원 API")
public interface MemberControllerDocs {

    @Operation(summary = "회원 정보 조회")
    ApiResponse<MemberResDTO.Detail> getMember(Long memberId);
}
```

구현 로직이나 default 메서드는 작성하지 않는다.

## Controller 예시
```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController implements MemberControllerDocs {

    private final MemberQueryService memberQueryService;

    @Override
    @GetMapping("/{memberId}")
    public ApiResponse<MemberResDTO.Detail> getMember(
        @PathVariable Long memberId
    ) {
        MemberResDTO.Detail result =
            memberQueryService.getMember(memberId);

        return ApiResponse.onSuccess(
            MemberSuccessCode.MEMBER_INFO_FETCH_SUCCESS,
            result
        );
    }
}
```

## 금지 사항
- `ResponseEntity` 반환
- Repository 직접 호출
- Entity 생성 또는 수정
- Controller 내부 DTO 변환
- 비즈니스 조건 분기
- `@Transactional`
- 도메인 예외 직접 처리
- `ApiResponse.onFailure` 직접 호출
- Docs 인터페이스에 구현 로직 작성
