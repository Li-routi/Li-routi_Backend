# Spring Boot DTO Convention

## 1. 기본 원칙
Codex는 DTO를 생성·수정·리뷰할 때 아래 규칙을 우선 적용한다.
각 도메인은 요청 DTO와 응답 DTO를 패키지와 파일 단위로 분리한다.

## 2. 패키지 구조
```text
{domain}/dto/
├── request/
│   └── {Domain}ReqDTO.java
└── response/
    └── {Domain}ResDTO.java
```
예시:
```text
member/dto/request/MemberReqDTO.java
member/dto/response/MemberResDTO.java
```
패키지 선언은 실제 디렉터리와 일치해야 한다.
```java
package com.example.project.domain.member.dto.request;
package com.example.project.domain.member.dto.response;
```

## 3. 명명 및 구성
- 요청 파일: `{Domain}ReqDTO.java`
- 응답 파일: `{Domain}ResDTO.java`
- 용도별 DTO는 해당 클래스 내부의 `public record`로 정의한다.
- 응답 DTO의 내부 record에는 `@Builder`를 선언한다.
- 내부 record에는 `Request`, `Response`, `DTO` 접미사를 붙이지 않는다.
- 최상위 클래스는 `public final class`와 `private` 생성자를 사용한다.

```java
public final class MemberResDTO {
    private MemberResDTO() {
    }

    @Builder
    public record Detail(...) {
    }

}
```
사용 예시: `MemberReqDTO.SignUp`, `MemberResDTO.Detail`

## 4. 요청 DTO
요청 DTO는 `dto.request` 패키지에 작성한다.
입력값만으로 판단 가능한 규칙은 record 컴포넌트에 validation으로 선언한다.

```java
public record SignUp(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 30,
              message = "비밀번호는 8자 이상 30자 이하여야 합니다.")
        String password
) {
}
```
- Spring Boot 3 기준 `jakarta.validation`을 사용한다.
- 주요 validation에는 구체적인 오류 메시지를 작성한다.
- 중첩 DTO에는 `@Valid`를 적용한다.
- null 여부, 길이, 범위, 형식은 요청 DTO에서 검증한다.
- 중복, 존재 여부, 권한, 상태 검증은 Service에서 처리한다.
- 요청 DTO는 Service와 Repository에 의존하지 않는다.

## 5. 응답 DTO
응답 DTO는 `dto.response` 패키지에 작성한다.
응답 DTO에는 validation annotation을 작성하지 않는다.

```java
public record Detail(
        Long id,
        String email,
        String name
) {}
```
- Controller에서 Entity를 직접 반환하지 않는다.
- 응답 DTO는 값만 담고, 실제 변환은 Converter에서 처리한다.
- 복잡한 조합은 Service에서 필요한 값을 준비한 뒤 Converter에서 변환한다.
- 민감 정보와 불필요한 연관관계를 노출하지 않는다.

## 6. Controller 사용
```java
@PostMapping
public ApiResponse<MemberResDTO.Detail> signUp(
        @Valid @RequestBody MemberReqDTO.SignUp request
) {
    MemberResDTO.Detail result = memberService.signUp(request);
    return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_SIGNUP_SUCCESS, result);
}
```
요청 DTO에는 `@Valid`를 적용한다.
Entity를 API 요청 또는 응답 타입으로 직접 사용하지 않는다.

## 7. 금지 사항
- `dto` 바로 아래에 요청·응답 DTO를 배치하지 않는다.
- 요청과 응답을 `MemberDTO` 하나에 함께 정의하지 않는다.
- 용도별 DTO를 최상위 파일로 무분별하게 분리하지 않는다.
- DTO에 Lombok의 `@Data`, `@Getter`를 사용하지 않는다. 단, 응답 DTO 내부 record에는 `@Builder`를 사용한다.
- `javax.validation`을 사용하지 않는다.
- DTO에서 DB 조회나 비즈니스 처리를 수행하지 않는다.
- Entity를 API 응답으로 직접 노출하지 않는다.

## 8. 체크리스트
1. `dto/request`, `dto/response`가 분리되었는가?
2. 파일명이 `{Domain}ReqDTO`, `{Domain}ResDTO` 형식인가?
3. 세부 DTO가 내부 `public record`인가?
4. 요청 DTO에 `jakarta.validation`이 적용되었는가?
5. DB 조회가 필요한 검증은 Service에 있는가?
6. Controller에 `@Valid`가 적용되었는가?
7. Entity가 API 요청·응답에 직접 노출되지 않는가?
8. 응답에 민감 정보가 포함되지 않는가?
