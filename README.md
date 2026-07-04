# LiRouti Backend

> :pushpin: 프로젝트 소개는 추후 기획 확정 후 업데이트할 예정입니다.

---

### 💙 팀원 소개

|                                채윤지 (팀장)                                |                                  김준혁                                   |                                  오채현                                   |                                 최유성                                  |                                  이운학                                   |
|:-----------------------------------------------------------------------:|:-------------------------------------------------------------------------:|:-------------------------------------------------------------------------:|:------------------------------------------------------------------------:|:-------------------------------------------------------------------------:|
| <img src="https://github.com/KateteDeveloper.png" width="150" height="150"> | <img src="https://github.com/ddo0122.png" width="150" height="150"> | <img src="https://github.com/ochyeon.png" width="150" height="150"> | <img src="https://github.com/yousung1020.png" width="150" height="150"> | <img src="https://github.com/dldnsgkr.png" width="150" height="150"> |
|          [@KateteDeveloper](https://github.com/KateteDeveloper)          |            [@ddo0122](https://github.com/ddo0122)            |            [@ochyeon](https://github.com/ochyeon)             |         [@yousung1020](https://github.com/yousung1020)          |            [@dldnsgkr](https://github.com/dldnsgkr)             |
|                              추후 업데이트 예정                              |                              추후 업데이트 예정                              |                              추후 업데이트 예정                              |                             추후 업데이트 예정                             |                              추후 업데이트 예정                              |

> 담당 업무는 기능별 작업 분배가 확정되는 대로 업데이트합니다.

---

## 🛠 기술 스택 및 환경

- **Backend**
  - Java 21
  - Spring Boot 4.1.0
  - Gradle 8.14.2

- **Database**
  - MySQL (mysql-connector-j)
  - Spring Data JPA / Hibernate ORM
  - QueryDSL (io.github.openfeign.querydsl 7.0)

- **캐싱**
  - Redis (spring-boot-starter-data-redis)

- **보안 및 인증**
  - Spring Security
  - JWT (jjwt 0.12.x)

- **API 문서화**
  - Swagger (springdoc-openapi-starter-webmvc-ui 3.0.1)

- **주요 라이브러리**
  - Lombok

---

## 📂 프로젝트 구조

도메인 주도(Domain-Driven) 패키지 구조를 따릅니다.

```
lirouti/
└── src/
    ├── main/
    │   ├── java/com/lirouti/
    │   │   ├── domain/
    │   │   │   ├── auth/
    │   │   │   ├── member/
    │   │   │   ├── home/
    │   │   │   ├── housework/
    │   │   │   ├── consumable/
    │   │   │   ├── shop/
    │   │   │   ├── order/
    │   │   │   ├── notification/
    │   │   │   └── onboarding/
    │   │   │       ├── controller/   # API 엔드포인트
    │   │   │       ├── converter/    # Entity <-> DTO 변환
    │   │   │       ├── dto/          # request / response
    │   │   │       ├── entity/       # JPA 엔티티
    │   │   │       ├── exception/    # 도메인별 예외, 에러/성공 코드
    │   │   │       ├── repository/   # 데이터 접근 계층
    │   │   │       └── service/      # 비즈니스 로직 (command / query)
    │   │   └── global/
    │   │       ├── apiPayload/       # 공통 응답, 예외 처리
    │   │       ├── auth/             # 인증 필터
    │   │       ├── config/           # 설정
    │   │       ├── entity/           # 공통 엔티티(BaseEntity 등)
    │   │       ├── properties/       # 설정 프로퍼티
    │   │       └── util/             # 유틸리티
    │   └── resources/
    │       └── application.yaml
    └── test/
        └── java/com/lirouti/
```

---

## 📌 Branch 전략

### Branch 종류 및 역할

| 브랜치 | 설명 |
| --- | --- |
| `main` | 실제 배포용 브랜치 |
| `develop` | 개발 통합 브랜치 |
| `feat/#이슈번호-기능요약` | 새로운 기능 개발 시 |
| `hotfix/#이슈번호-기능요약` | 긴급 버그 수정 시 |
| `refactor/#이슈번호-리팩토링요약` | 리팩토링 시 |

> 💡 브랜치명 형식: `타입/#이슈번호-기능요약`

✅ 예시
- `feat/#12-kakao-login`

---

## ✅ Commit 규칙

### 커밋 메시지 형식

```
타입: 주제

본문(선택)
```

### 타입 종류

| 타입 | 설명 | 예시 |
| --- | --- | --- |
| `feat` | 새로운 기능 추가 | `feat: add social login` |
| `fix` | 버그 수정 | `fix: resolve token expiry bug` |
| `hotfix` | 운영 중 긴급 버그 수정 | `hotfix: patch payment crash` |
| `refactor` | 기능 변경 없는 코드 구조 개선 | `refactor: extract auth service` |
| `perf` | 성능 개선 | `perf: optimize query indexing` |
| `style` | 코드 포맷팅, 세미콜론 등 (동작 변화 없음) | `style: apply prettier` |
| `design` | UI/CSS 등 디자인 변경 | `design: update button styles` |
| `docs` | 문서 수정 (README, 주석 등) | `docs: update API guide` |
| `comment` | 주석 추가/변경 | `comment: add function docs` |
| `test` | 테스트 코드 추가/수정 | `test: add login unit tests` |
| `build` | 빌드 시스템, 의존성 변경 | `build: bump next to 14.2` |
| `ci` | CI 설정 변경 (GitHub Actions 등) | `ci: fix OOM in build step` |
| `chore` | 기타 잡일 (설정, 패키지 등) | `chore: update gitignore` |
| `rename` | 파일/폴더명 변경 | `rename: move utils to lib` |
| `remove` | 파일 삭제 | `remove: delete legacy api` |
| `revert` | 이전 커밋 되돌리기 | `revert: feat add social login` |

✅ 예시
- `feat: 카카오 소셜 로그인 관련 jwt 발행 완료`

---

## 🔀 PR 규칙

- `main` 브랜치에 직접 push 금지
- merge 전 빌드/테스트 진행해보기
- PR 템플릿에 타이트하게 맞추지 않고 유동적으로 작성하되, 의미가 명확하게 전달되도록 작성

---

## 📦 공통 응답 처리

모든 API 응답은 [`ApiResponse<T>`](src/main/java/com/lirouti/global/apiPayload/ApiResponse.java)로 감싸서 반환합니다.

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "string",
  "result": {}
}
```

- `isSuccess`: 성공 여부
- `code`: 도메인별 비즈니스 코드 (예: `COMMON400_1`, `AUTH403_1`)
- `message`: 프론트엔드 쪽에서 참고할 메시지
- `result`: 실제 응답 데이터. 실패 시에는 보통 `null` 반환

---

## ⚠️ 공통 예외 처리


- 모든 예외는 [`GeneralExceptionAdvice`](src/main/java/com/lirouti/global/apiPayload/handler/GeneralExceptionAdvice.java) (`@RestControllerAdvice`) 기반의 전역 예외 처리 클래스에서 처리
- 컨트롤러/서비스에서는 예외를 직접 잡지 않고, [`GeneralException`](src/main/java/com/lirouti/global/apiPayload/exception/GeneralException.java)에 에러 코드를 실어 throw 하는 방식으로 통일
- 예외 상태는 `BaseErrorCode`를 구현한 ENUM 클래스로 관리 (UMC 워크북과 비슷한 방식)
  - 공통 에러: [`GeneralErrorCode`](src/main/java/com/lirouti/global/apiPayload/code/GeneralErrorCode.java)
  - 도메인별 에러: 각 `domain/{도메인}/exception/code/error` 패키지에 정의

---

## Copyright

© 2026 LiRouti Team. All rights reserved.

This project and its source code are proprietary and confidential.
Unauthorized copying, modification, distribution, or use of this software is strictly prohibited.
