# Service Convention

## 목적

Service는 애플리케이션 유스케이스와 트랜잭션 경계를 담당한다.
Controller, Repository, Converter의 책임을 침범하지 않는다.

## 패키지 구조

```text
{domain}/service/
├── {Domain}QueryService.java
└── {Domain}CommandService.java
```

조회 기능이 없는 도메인은 CQRS를 강제하지 않는다.
예: `AuthService`, `TokenService`.

## 명명 및 CQRS

- 조회 서비스: `{Domain}QueryService`
- 변경 서비스: `{Domain}CommandService`
- CQRS 예외 서비스: `{Domain}Service`
- 불필요한 Service 인터페이스는 만들지 않는다.

### QueryService

- 조회만 담당한다.
- 엔티티 상태를 변경하지 않는다.
- `@Transactional(readOnly = true)`를 사용한다.
- 조회 결과는 Response DTO로 반환한다.
- DTO 변환은 Converter에 위임한다.

### CommandService

- 생성, 수정, 삭제, 상태 변경을 담당한다.
- `@Transactional`을 사용한다.
- 조회 전용 API를 제공하지 않는다.
- 엔티티는 의미 있는 도메인 메서드로 변경한다.

## 트랜잭션

- `@Transactional`은 클래스가 아닌 public 메서드에 선언한다.
- 조회 메서드는 `readOnly = true`를 사용한다.
- private 메서드에는 선언하지 않는다.
- 같은 클래스 내부 호출로 트랜잭션 전파를 기대하지 않는다.
- 트랜잭션 안에서 장시간 외부 API를 호출하지 않는다.

## 책임

Service가 담당하는 항목:

- 유스케이스 실행과 비즈니스 흐름 조정
- Repository를 통한 조회 및 저장
- 트랜잭션 경계 설정
- 비즈니스 예외 발생
- 여러 도메인 객체의 작업 조합

Service가 담당하지 않는 항목:

- HTTP 요청 및 응답 처리
- Swagger 문서화
- `ResponseEntity` 생성
- validation annotation 정의
- 단순 객체 매핑 구현

## 의존성

- Repository와 Converter를 주입받을 수 있다.
- QueryService와 CommandService가 서로 의존하지 않도록 한다.
- Service 간 연쇄 호출은 최소화한다.
- 복잡한 다중 도메인 흐름은 Facade 또는 Application Service를 검토한다.
- 순환 의존을 만들지 않는다.

## 예외 및 반환

- 조회 실패나 규칙 위반 시 도메인 예외를 발생시킨다.
- `IllegalArgumentException`을 비즈니스 예외 대용으로 남용하지 않는다.
- 예외 응답 변환은 전역 예외 처리기에 위임한다.
- Entity를 Controller에 직접 반환하지 않는다.
- 생성은 식별자 또는 결과 DTO를 반환할 수 있다.
- 수정과 삭제는 응답이 불필요하면 `void`를 사용한다.

## 예시

```java
@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final MemberRepository memberRepository;
    private final MemberConverter memberConverter;

    @Transactional(readOnly = true)
    public MemberResDTO.Detail getMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        return memberConverter.toDetail(member);
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class MemberCommandService {

    private final MemberRepository memberRepository;
    private final MemberConverter memberConverter;

    @Transactional
    public Long createMember(MemberReqDTO.Create request) {
        return memberRepository.save(memberConverter.toEntity(request)).getId();
    }
}
```

## 금지 사항

- 클래스 레벨 `@Transactional`
- QueryService의 상태 변경
- CommandService의 조회 API 혼합
- Service의 `ResponseEntity` 및 Swagger 사용
- Service의 DTO 직접 조립 반복
- Repository 결과에 무조건 `.get()` 호출
- 다른 Service의 무분별한 연쇄 호출

## 체크리스트

- Query와 Command 책임이 분리되었는가?
- 트랜잭션이 메서드 단위인가?
- 조회 메서드에 `readOnly = true`가 적용되었는가?
- HTTP 계층 타입에 의존하지 않는가?
- Entity를 직접 반환하지 않는가?
- 변환을 Converter에 위임했는가?
