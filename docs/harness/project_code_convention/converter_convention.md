# Converter Convention

## 목적
Converter는 DTO와 Entity 사이의 변환과 응답 조립을 담당한다.
DTO 내부에 변환 책임을 두지 않고, 객체 생성과 조합 책임을 별도 계층으로 분리한다.

## 패키지 구조
```text
{domain}/converter/{Domain}Converter.java
```

## 클래스 규칙
- 파일명은 `{Domain}Converter`로 작성한다.
- `final class`와 `private` 생성자를 사용한다.
- 모든 변환 메서드는 `static`으로 작성한다.
- 상태 필드와 외부 의존성을 두지 않는다.

```java
public final class MemberConverter {
    private MemberConverter() {
    }
}
```

## 메서드 명명
- Request DTO → Entity: `toEntity`
- Entity → 결과 응답: `toResult`
- Entity → 상세 응답: `toDetail`
- Entity → 요약 응답: `toSummary`
- 목록 응답: `toResponseList`
- Page 응답: `toPage`

응답 DTO 이름이 명확하면 해당 이름에 맞춰 명명한다.

## 객체 생성 방식
- Entity와 Response DTO는 Builder 사용을 기본으로 한다.
- Entity 생성 시 public setter를 사용하지 않는다.
- 역할, 활성 여부 등 생성 기본값은 명시적으로 설정한다.
- Builder에는 메서드 인자나 원본 객체에서 얻은 값만 전달한다.
- 암호화, 조회, 외부 API 처리 결과는 매개변수로 전달받는다.

## 책임
Converter는 다음 작업을 담당한다.
- Request DTO를 Entity로 변환
- Entity를 Response DTO로 변환
- 여러 값을 하나의 객체로 조합
- 목록과 Page 결과 변환
- 반복되는 단순 매핑 코드 캡슐화

Converter는 다음 작업을 담당하지 않는다.
- Repository 조회 또는 Service 호출
- 권한 검사, 중복 검사, 상태 판단
- 비밀번호 암호화 또는 외부 API 호출
- 트랜잭션 처리
- 비즈니스 예외 발생
- HTTP 응답 생성
- 기존 Entity 상태 변경

## 변환 규칙
- DTO 내부에 변환 책임을 두지 않는다.
- Controller와 Service에서 DTO 조립 코드를 반복하지 않는다.
- 연관 Entity나 외부 처리 결과가 필요한 경우 Service에서 조회하거나 계산한 뒤 매개변수로 전달한다.
- 소셜 정보나 암호화된 비밀번호도 매개변수로 전달한다.
- Converter는 전달받은 값을 Builder에 매핑하는 역할만 수행한다.
- 컬렉션 결과는 null 대신 빈 컬렉션으로 반환한다.

## 예시
```java
public final class MemberConverter {
    private MemberConverter() {
    }

    public static MemberResDTO.Result toResult(Member member) {
        return MemberResDTO.Result.builder()
            .id(member.getId())
            .email(member.getEmail())
            .nickname(member.getNickname())
            .role(member.getRole())
            .createdAt(member.getCreatedAt())
            .build();
    }

    public static Member toEntity(
        MemberReqDTO.Signup request,
        String encodedPassword
    ) {
        return Member.builder()
            .email(request.email())
            .password(encodedPassword)
            .nickname(request.nickname())
            .role(Role.ROLE_USER)
            .isActive(true)
            .build();
    }

    public static MemberResDTO.Detail toDetail(
        Member member,
        List<Housework> houseworks
    ) {
        return MemberResDTO.Detail.builder()
            .id(member.getId())
            .nickname(member.getNickname())
            .houseworks(HouseworkConverter.toSummaryList(houseworks))
            .build();
    }
}
```

추가 정보가 필요한 변환은 Converter가 직접 조회하지 않고 모두 인자로 전달받는다.

## 목록과 Page
```java
public static List<MemberResDTO.Result> toResultList(List<Member> members) {
    return members.stream()
        .map(MemberConverter::toResult)
        .toList();
}

public static Page<MemberResDTO.Result> toPage(Page<Member> members) {
    return members.map(MemberConverter::toResult);
}
```

## null 처리
- 필수 입력의 null을 조용히 허용하지 않는다.
- null을 기본 객체로 임의 변환하지 않는다.
- 선택 필드는 프로젝트 정책에 따라 null을 유지할 수 있다.
- null 여부로 비즈니스 결정을 내리지 않는다.
- null 검증은 Controller의 요청 검증 또는 Service의 입력 검증에서 수행하고, Converter는 이미 검증된 값만 전달받는 것을 전제로 한다.

## 금지 사항
- DTO 내부 변환 메서드
- Converter 인스턴스 생성
- Repository 또는 Service 의존
- `@Transactional`
- 암호화 또는 외부 API 호출
- 비즈니스 조건 분기와 예외 발생
- 기존 Entity 상태 변경
- public setter 기반 객체 생성

## 체크리스트
- `final class`, `private` 생성자, `static` 메서드 구조인가?
- Entity와 Response DTO를 Builder로 생성하는가?
- 기본값을 명시적으로 설정했는가?
- 외부 처리 결과를 매개변수로 전달받는가?
- DTO에 변환 메서드가 없는가?
- 순수한 객체 변환만 수행하는가?
