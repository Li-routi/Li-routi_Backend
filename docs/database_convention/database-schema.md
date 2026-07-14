# 데이터 모델 가이드

> 기준: 제공된 DDL을 바탕으로 검토한 목표 스키마. 이 문서는 AI와 개발자가 도메인 모델을 빠르게 파악하고 일관된 구현 결정을 내리기 위한 가이드다. 실행 가능한 마이그레이션 명세는 별도로 관리한다.

## 먼저 알아둘 규칙

- 모든 테이블의 기본 키는 `BIGINT id`다. 제공된 DDL에는 자동 생성 전략이 명시되어 있지 않다.
- `DATETIME(6)`은 마이크로초 단위 시각이고, `DATE`는 날짜만 저장한다. 날짜 기반 주기 계산에는 `DATE`를, 실제 이벤트 시각에는 `DATETIME(6)`을 사용한다.
- `deleted_at`이 있는 테이블은 소프트 삭제 대상이다. 일반 조회에서는 반드시 `deleted_at IS NULL`인 데이터만 다룬다. 물리 삭제는 외래 키와 이력 보존에 미치는 영향을 검토한 뒤에만 수행한다.
- `active`는 현재 사용 가능한 마스터/루틴인지 나타낸다. 소프트 삭제와 다르므로 `active = false` 레코드는 관리·이력 화면에서 필요할 수 있다.
- `notification_enabled`, `subscription_enabled`, `auto_delivery_enabled`, `onboarding_completed`는 `TINYINT(1)` 불리언 플래그다.
- 외래 키에는 삭제/수정 전파 규칙이 없다. 부모 레코드를 물리 삭제하면 참조 무결성 오류가 발생할 수 있다.

## 도메인 모델

### 회원이 소유하는 데이터

`member`가 사용자 계정의 루트다. 회원이 직접 소유하는 데이터는 다음과 같다.

- `member_consumable`: 회원이 추적하는 개별 소모품
- `member_housework_routine`: 회원별 집안일 주기
- `member_challenge`: 회원별 챌린지 참여 상태와 연속 참여일
- `member_order`: 회원 주문
- `notification`: 회원에게 전달할 알림

회원 탈퇴 또는 삭제 기능을 구현할 때는 위 데이터를 포함한 소프트 삭제 범위와 접근 차단 정책을 함께 정의해야 한다. 현재 DDL만으로는 연쇄 소프트 삭제 규칙이 정해져 있지 않다.

### 마스터 데이터

- `consumable_category`: 소모품 분류와 기본 사용 주기
- `housework_template`: 공통 집안일 템플릿과 기본 주기
- `challenge`: 앱이 제공하는 챌린지. 회원이 직접 생성할 수 없다.
- `product`: 판매 상품. 선택적으로 `consumable_category`에 속한다.
- `notification_type`: 알림 코드, 제목·본문 템플릿, 대상 유형

마스터 데이터는 `active`로 노출 여부를 제어한다. 이미 사용된 템플릿, 카테고리, 상품은 이력 데이터가 참조할 수 있으므로 비활성화와 소프트 삭제의 사용 목적을 구분한다.

### 이력 및 거래 데이터

- `member_housework_completion_log`: 특정 집안일 루틴의 완료 이력. `canceled_at`이 있으면 완료 처리가 취소된 이력이다.
- `challenge_verification`: 챌린지 참여의 일자별 인증 이력. 하루에 한 건만 존재한다.
- `challenge_verification_report`: 인증에 대한 신고. 신고자 본인의 피드에서만 해당 인증을 숨기는 용도다.
- `consumable_purchase_log`: 소모품 구매·보충 이력. 주문과 상품 연결은 선택 사항이므로 수동 등록도 가능하다.
- `order_detail`: 주문에 포함된 상품과 주문 당시 가격 스냅샷. 상품의 현재 가격이 바뀌어도 `unit_price`, `total_price`는 주문 이력으로 유지한다.
- `payment`: 주문의 결제 시도·승인·실패 정보. 현 스키마상 한 주문에 복수 결제 레코드를 둘 수 있다.

## 구현 시 관계 해석

- `member_consumable`은 `member`에 반드시 속하고, `consumable_category`는 선택 사항이다.
- `member_housework_routine`은 `member`에 반드시 속하며, `housework_template`을 선택적으로 참조한다. 템플릿 없이 회원이 직접 만든 루틴도 허용한다.
- `member_housework_completion_log`은 반드시 하나의 `member_housework_routine`에 속한다.
- `member_challenge`는 `member`와 `challenge`를 반드시 참조한다. 집안일과 달리 회원이 직접 만든 챌린지는 없으므로 `challenge_id`는 선택 사항이 아니다.
- `challenge_verification`은 반드시 하나의 `member_challenge`에 속한다. `challenge`나 `member`를 직접 참조하지 않는다.
- `challenge_verification_report`는 `challenge_verification`과 신고자 `member`를 반드시 참조한다.
- `member_order`은 반드시 하나의 `member`에 속한다. `order_detail`, `payment`은 주문을 참조한다.
- `product`는 선택적으로 `consumable_category`에 속한다. `order_detail`은 상품을 반드시 참조하며, `consumable_purchase_log`의 상품 참조는 선택 사항이다.
- `notification`은 `member`와 `notification_type`을 반드시 참조한다. 단, 알림 유형 외래 키 컬럼의 실제 이름은 `id2`다. 코드와 향후 마이그레이션에서 이를 `notification_type_id`로 혼동하지 않도록 주의한다.
- `member_routine`은 현재 어느 테이블과도 외래 키로 연결되지 않으며 `member_id`도 없다. 이름만으로 회원 소유 엔티티라고 가정하지 않는다.

## 주요 비즈니스 흐름

### 소모품 관리

회원은 `member_consumable`에 소모품명, 최근 구매일, 평균 사용 일수를 기록한다. `expected_depletion_date`는 알림과 자동 배송 판단에 쓰일 수 있는 파생 값이다. 구매가 발생하면 `consumable_purchase_log`를 남기고, 필요에 따라 해당 소모품의 최근 구매일·예상 소진일을 갱신한다.

### 집안일 관리

템플릿을 기반으로 하거나 직접 만든 `member_housework_routine`에 반복 주기와 다음 예정일을 둔다. 완료하면 `member_housework_completion_log`를 생성하고, 루틴의 `last_performed_date` 및 `next_due_date`를 갱신한다. 완료 취소는 로그를 지우기보다 `canceled_at`을 기록하는 방식으로 해석한다.

### 챌린지

`challenge`는 앱이 제공하는 마스터 데이터이고, 회원은 `member_challenge`로 참여한다. 인증하면 `challenge_verification`을 생성하고, 같은 트랜잭션에서 참여의 `current_streak`과 `last_verified_date`를 갱신한다.

연속 참여일은 인증 시점에 다음 규칙으로 계산한다.

- `last_verified_date`가 어제면 `current_streak`을 1 증가시킨다.
- `last_verified_date`가 오늘이면 이미 인증한 상태이므로 갱신하지 않는다. 하루 1회 제약에 걸린다.
- 그보다 오래되었거나 값이 없으면 `current_streak`을 1로 초기화한다.

**연속이 끊기는 시점에는 어떤 이벤트도 발생하지 않는다.** 회원이 이틀을 쉬어도 이를 감지해 `current_streak`을 0으로 바꿔주는 주체가 없다. 따라서 배치로 초기화하지 않고, **조회 시점에 `last_verified_date`가 어제보다 오래되었으면 연속 참여일을 0으로 판정한다.** 저장된 값과 화면에 표시되는 값이 다를 수 있다는 뜻이므로, 조회 로직에서 반드시 이 판정을 거친다.

날짜 기준은 KST 자정이다. `verified_date`는 `DATE`, `verified_at`은 실제 인증 시각(`DATETIME(6)`)이다.

인증은 당일 것만 다시 올릴 수 있다. 이때 행을 지우고 새로 만드는 것이 아니라 **기존 행을 덮어쓴다.** 하루에 한 행이라는 사실이 유지되므로 유니크 제약과 충돌하지 않고, 그 인증을 참조하는 신고 데이터도 그대로 남는다. 어제 이전의 인증은 수정할 수 없으므로 연속 참여일을 소급해 재계산하는 경우는 없다. 집안일의 `canceled_at`에 해당하는 컬럼을 두지 않는 이유다.

챌린지를 그만두면 `member_challenge.active`를 `false`로 둔다. 다시 참여하면 같은 행을 재활성화하고 연속 참여일을 0으로 초기화한다. 참여 이력을 새 행으로 쌓지 않으므로 `(member_id, challenge_id)` 유니크 제약을 유지할 수 있다.

신고는 인증을 삭제하지 않는다. 피드를 조회할 때 **신고자 본인에게만** 해당 인증을 제외한다. 다른 회원에게는 그대로 노출된다.

### 주문과 결제

`member_order`는 배송·수령 정보와 총액을 보관하는 주문 헤더이며, 구매 품목은 `order_detail`에 저장한다. 결제 요청부터 승인 또는 실패까지의 상태와 외부 거래 식별자는 `payment`에 저장한다. 결제 실패 이력을 보존할 수 있으므로 단순히 주문당 결제 1건이라고 가정하지 않는다.

### 알림

`notification_type`은 재사용 가능한 알림 정책·문구 템플릿이고, `notification`은 회원에게 예약된 개별 발송 건이다. `scheduled_at`, `sent_at`, `read_at`은 각각 예약, 발송, 읽음 상태를 나타낸다.

## 테이블별 필드 참고

아래는 각 테이블의 주요 필드다. 구현 시에는 이 문서의 의미 해석을 우선하고, 타입·제약은 실제 마이그레이션 또는 엔티티 정의와 대조한다.

## 회원 및 알림 테이블

### `member`

회원 계정과 온보딩 상태를 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| email | VARCHAR(255) | N | 이메일. 유니크 |
| nickname | VARCHAR(50) | N | 닉네임 |
| social_provider | VARCHAR(50) | N | 소셜 로그인 제공자 |
| social_id | VARCHAR(255) | N | 제공자 내 사용자 식별자 |
| role | VARCHAR(50) | N | 권한 |
| onboarding_completed | TINYINT(1) | N | 온보딩 완료 여부, 기본값 `0` |
| is_active | TINYINT(1) | N | 회원 활성 여부(탈퇴 시 `0`), 기본값 `1` |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

- `UNIQUE(social_provider, social_id)` — 소셜 회원 식별자. `email`도 유니크다.
- 회원 식별은 `email`이 아니라 `social_provider + social_id` 조합이다.
- 위 내용은 실제 `Member.java`를 기준으로 정정한 것이다. 이전 문서는 컬럼명을 `provider`로 적고 소셜 관련 컬럼을 NULL 허용으로 표기했으며 `is_active`가 누락되어 있었다.

### `notification_type`

알림의 종류와 발송 문구 템플릿을 정의한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| code | VARCHAR(50) | Y | 알림 유형 코드 |
| name | VARCHAR(100) | Y | 유형명 |
| description | VARCHAR(255) | Y | 설명 |
| title | VARCHAR(100) | Y | 알림 제목 템플릿 |
| message | VARCHAR(500) | Y | 알림 본문 템플릿 |
| target_type | ENUM(50) | Y | 대상 유형 |
| active | TINYINT(1) | Y | 사용 여부 |
| created_at / updated_at / deleted_at | DATETIME(6) | Y / Y / Y | 생성·수정·소프트 삭제 시각 |

### `notification`

회원에게 예약·발송되는 알림 이력이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 수신 회원, `member.id` FK |
| id2 | BIGINT | N | 알림 유형, `notification_type.id` FK |
| scheduled_at | DATETIME(6) | N | 발송 예정 시각 |
| sent_at | DATETIME(6) | Y | 실제 발송 시각 |
| read_at | DATETIME(6) | Y | 읽음 시각 |
| created_at / updated_at / deleted_at | DATETIME(6) | N / Y / Y | 생성·수정·소프트 삭제 시각 |

`id2`는 DDL에 정의된 실제 컬럼명이다. 의미상 `notification_type_id`이므로, 향후 마이그레이션에서 이름 변경을 검토할 수 있다.

## 소모품 및 상품 테이블

### `consumable_category`

소모품의 공통 분류와 기본 사용 주기를 관리한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| name | VARCHAR(100) | N | 카테고리명 |
| default_usage_days | INT | Y | 기본 사용 일수 |
| active | TINYINT(1) | N | 사용 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

### `member_consumable`

회원이 관리하는 개별 소모품이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 소유 회원, `member.id` FK |
| consumable_category_id | BIGINT | Y | 카테고리, `consumable_category.id` FK |
| name | VARCHAR(100) | N | 소모품명 |
| last_purchase_date | DATE | Y | 마지막 구매일 |
| average_usage_days | INT | N | 평균 사용 일수 |
| expected_depletion_date | DATE | Y | 예상 소진일 |
| notification_enabled | TINYINT(1) | N | 알림 사용 여부, 기본값 `0` |
| subscription_enabled | TINYINT(1) | N | 구독 사용 여부, 기본값 `0` |
| auto_delivery_enabled | TINYINT(1) | N | 자동 배송 사용 여부, 기본값 `0` |
| active | TINYINT(1) | N | 사용 여부, 기본값 `1` |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

### `product`

쇼핑 상품 정보다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| consumable_category_id | BIGINT | Y | 카테고리, `consumable_category.id` FK |
| name | VARCHAR(150) | N | 상품명 |
| description | TEXT | Y | 상품 설명 |
| price | INT | N | 판매 가격 |
| image_url | VARCHAR(2048) | Y | 이미지 URL |
| active | TINYINT(1) | N | 판매 여부, 기본값 `1` |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

### `consumable_purchase_log`

소모품의 구매·보충 이력이다. 주문 또는 상품 연결은 선택 사항이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_consumable_id | BIGINT | N | 대상 소모품, `member_consumable.id` FK |
| member_order_id | BIGINT | Y | 연결 주문, `member_order.id` FK |
| product_id | BIGINT | Y | 연결 상품, `product.id` FK |
| purchase_type | VARCHAR(50) | N | 구매 유형 |
| purchased_date | DATE | N | 구매일 |
| quantity | INT | Y | 수량 |
| memo | VARCHAR(255) | Y | 메모 |
| created_at / updated_at / deleted_at | DATETIME(6) | N / Y / Y | 생성·수정·소프트 삭제 시각 |

## 집안일 테이블

### `housework_template`

공통 집안일 템플릿이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| name | VARCHAR(100) | N | 템플릿명 |
| description | VARCHAR(255) | Y | 설명 |
| default_cycle_days | INT | Y | 기본 반복 주기(일) |
| active | TINYINT(1) | N | 사용 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

### `member_housework_routine`

회원별 집안일 반복 작업이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 소유 회원, `member.id` FK |
| housework_template_id | BIGINT | Y | 원본 템플릿, `housework_template.id` FK |
| name | VARCHAR(100) | N | 작업명 |
| last_performed_date | DATE | Y | 마지막 수행일 |
| cycle_days | INT | N | 반복 주기(일) |
| next_due_date | DATE | Y | 다음 예정일 |
| notification_enabled | TINYINT(1) | N | 알림 사용 여부, 기본값 `0` |
| active | TINYINT(1) | N | 사용 여부, 기본값 `1` |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

### `member_housework_completion_log`

집안일 루틴의 완료 및 취소 이력이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_housework_routine_id | BIGINT | N | 대상 루틴, `member_housework_routine.id` FK |
| completed_date | DATE | N | 완료 기준일 |
| completed_at | DATETIME(6) | N | 완료 처리 시각 |
| canceled_at | DATETIME(6) | Y | 완료 취소 시각 |
| created_at / updated_at / deleted_at | DATETIME(6) | N / Y / Y | 생성·수정·소프트 삭제 시각 |

### `member_routine`

회원 소유로 보이는 일반 루틴 테이블이다. 제공된 DDL에는 `member_id` 외래 키가 없어, 현재 회원과의 직접 관계는 정의되지 않는다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| title | VARCHAR(100) | N | 루틴 제목 |
| next_due_date | DATE | Y | 다음 예정일 |
| active | TINYINT(1) | N | 사용 여부, 기본값 `1` |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

## 챌린지 테이블

기존 테이블과 달리, 아래 표에는 유니크 제약과 인덱스를 함께 명시한다. 하루 1회 인증과 중복 신고 방지는 애플리케이션 검증만으로는 동시 요청을 막을 수 없으므로 DB 제약으로 보장한다.

### `challenge`

앱이 제공하는 챌린지 마스터다. 회원이 직접 생성할 수 없다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| name | VARCHAR(100) | N | 챌린지명 |
| description | VARCHAR(255) | Y | 설명 |
| category | VARCHAR(50) | N | 분류. `HEALTH`, `EXERCISE`, `STUDY`, `LIFE`, `HOBBY` |
| active | TINYINT(1) | N | 노출 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `category`는 화면의 필터 칩과 다르다. 칩에 있는 `전체`와 `인기`는 분류가 아니라 각각 **필터 없음**과 **참여자 수 내림차순 정렬**을 뜻한다. 실제 분류는 위 5개다.
- 상시 운영이므로 시작일·종료일 컬럼을 두지 않는다.
- 참여자 수는 `member_challenge`를 집계해서 구한다. 캐시 컬럼(`participant_count`)을 두지 않는다. 인기순 정렬이 느려지면 그때 비정규화를 검토한다.
- 마스터 데이터이므로 소프트 삭제 대신 `active`로 노출을 제어한다.
- 이름 부분 검색을 지원한다. `LIKE '%키워드%'`는 인덱스를 타지 못하므로, 데이터가 늘어나면 검색 방식을 재검토한다.

### `member_challenge`

회원의 챌린지 참여 상태다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 참여 회원, `member.id` FK |
| challenge_id | BIGINT | N | 대상 챌린지, `challenge.id` FK |
| current_streak | INT | N | 연속 참여일, 기본값 `0` |
| last_verified_date | DATE | Y | 마지막 인증일. 참여 직후에는 값이 없다. |
| joined_at | DATETIME(6) | N | 참여 시각 |
| active | TINYINT(1) | N | 참여 중 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_id, challenge_id)`. 재참여는 새 행을 만들지 않고 기존 행의 `active`를 `true`로 되돌리며 `current_streak`을 0으로 초기화한다.
- 참여 중인 챌린지 조회를 위해 `(member_id, active)` 인덱스가 필요하다.
- 이탈은 `active = false`로 표현한다. 참여 이력을 보존하므로 소프트 삭제(`deleted_at`)를 쓰지 않는다.
- `current_streak`은 저장된 값을 그대로 신뢰하지 않는다. 조회 시 `last_verified_date`가 어제보다 오래되었으면 0으로 판정한다.

### `challenge_verification`

챌린지 참여의 일자별 인증 이력이다. 하루에 한 건만 존재한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_challenge_id | BIGINT | N | 대상 참여, `member_challenge.id` FK |
| verified_date | DATE | N | 인증 기준일(KST) |
| verified_at | DATETIME(6) | N | 인증 처리 시각 |
| image_url | VARCHAR(2048) | N | 인증 사진. 사진 없는 인증은 허용하지 않는다. |
| content | VARCHAR(255) | Y | 인증 코멘트 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_challenge_id, verified_date)`로 하루 1회 인증을 DB가 보장한다. 애플리케이션에서 "오늘 인증했는지" 조회한 뒤 저장하는 방식은 동시 요청 시 두 건이 들어갈 수 있다.
- 피드 조회를 위해 `(member_challenge_id, verified_at)` 인덱스가 필요하다. 챌린지 단위 피드는 `member_challenge`를 경유하므로 조인 비용을 확인한다.
- **이 테이블은 소프트 삭제하지 않는다.** `deleted_at`을 두지 않는 이유는 아래와 같다.
  - 당일 재인증은 삭제 후 재등록이 아니라 **기존 행의 `image_url`·`content`·`verified_at`을 덮어쓰는 방식(UPDATE)**으로 처리한다. 하루에 한 행이라는 사실이 변하지 않으므로 유니크 제약과 충돌하지 않는다.
  - `deleted_at`을 두면 소프트 삭제된 행도 유니크 제약에 그대로 남는다. MySQL의 유니크 제약은 `deleted_at IS NULL` 조건을 모르기 때문에, 같은 날 다시 인증하려 하면 삭제된 행 때문에 실패한다. 덮어쓰기 방식은 이 문제를 아예 만들지 않는다.
  - 덮어쓰기는 `challenge_verification_report`의 외래 키도 깨뜨리지 않는다. 행이 사라지지 않기 때문이다.
  - 어제 이전의 인증은 수정할 수 없으므로, 보존해야 할 이력이 삭제될 위험은 없다.
- 오늘 완료자 수는 `verified_date = 오늘`인 인증을 집계해 구한다.

### `challenge_verification_report`

인증 사진에 대한 신고다. 신고자 본인의 피드에서만 해당 인증을 숨기는 용도다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| challenge_verification_id | BIGINT | N | 신고 대상 인증, `challenge_verification.id` FK |
| reporter_id | BIGINT | N | 신고한 회원, `member.id` FK |
| reason | VARCHAR(255) | Y | 신고 사유 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(challenge_verification_id, reporter_id)`로 중복 신고를 막는다.
- 신고는 인증을 삭제하지 않는다. 피드 조회 시 신고자 본인에게만 제외한다.
- 신고 누적으로 전체 회원에게 숨기는 정책은 현재 없다. 따라서 `hidden_at` 같은 컬럼을 두지 않는다. 부적절한 사진이 계속 노출될 수 있다는 점은 인지하고 있으며, 필요해지면 별도로 다룬다.

## 주문 및 결제 테이블

### `member_order`

회원 주문의 헤더 정보다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 주문 회원, `member.id` FK |
| order_number | VARCHAR(100) | N | 주문 번호 |
| order_status | VARCHAR(50) | N | 주문 상태 |
| order_type | VARCHAR(50) | N | 주문 유형 |
| total_amount | INT | N | 총 주문 금액 |
| receiver_name | VARCHAR(50) | N | 수령인 이름 |
| receiver_phone | VARCHAR(30) | N | 수령인 연락처 |
| shipping_address | VARCHAR(255) | N | 배송지 |
| ordered_at | DATETIME(6) | N | 주문 시각 |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

### `order_detail`

주문에 포함된 상품 행이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_order_id | BIGINT | N | 주문, `member_order.id` FK |
| product_id | BIGINT | N | 상품, `product.id` FK |
| quantity | INT | N | 주문 수량 |
| unit_price | INT | N | 주문 당시 단가 |
| total_price | INT | N | 행 총액 |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

### `payment`

주문별 결제 요청과 승인·실패 상태를 기록한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_order_id | BIGINT | N | 대상 주문, `member_order.id` FK |
| payment_method | VARCHAR(50) | N | 결제 수단 |
| payment_status | VARCHAR(50) | N | 결제 상태 |
| amount | INT | N | 결제 금액 |
| transaction_id | VARCHAR(255) | Y | 외부 결제 거래 식별자 |
| requested_at | DATETIME(6) | N | 결제 요청 시각 |
| approved_at | DATETIME(6) | Y | 승인 시각 |
| failed_at | DATETIME(6) | Y | 실패 시각 |
| fail_reason | VARCHAR(255) | Y | 실패 사유 |
| created_at / updated_at / deleted_at | DATETIME(6) | N / N / Y | 생성·수정·소프트 삭제 시각 |

## DDL 확인 필요 사항

- 기본 키는 정의되어 있으나, `id`의 자동 생성 전략(`AUTO_INCREMENT` 등)은 DDL에 없다.
- 유니크 제약조건과 일반 인덱스가 정의되어 있지 않다. 예를 들어 `member.email`, `member_order.order_number`, 조회에 자주 쓰이는 외래 키 및 `deleted_at`은 요구사항에 따라 인덱스 검토가 필요하다.
- `payment.member_order_id`에는 유니크 제약조건이 없으므로, 현재 정의상 하나의 주문이 여러 결제 레코드를 가질 수 있다.
- `member_routine`은 이름과 달리 회원 식별자가 없으며 다른 테이블과의 외래 키도 없다.
- 챌린지 테이블은 위 표에 유니크 제약과 인덱스를 명시했다. 다른 테이블도 같은 수준으로 보완이 필요하다.
- 소프트 삭제(`deleted_at`)와 유니크 제약은 함께 쓰면 충돌한다. 삭제된 행도 유니크 제약에 남기 때문이다. `challenge_verification`은 덮어쓰기 방식을 택해 이 문제를 피했으나, **기존 테이블 중 `deleted_at`과 유니크 제약을 함께 가진 것이 있다면 같은 문제가 있는지 점검이 필요하다.**
- 챌린지 인증 사진은 외부 스토리지에 저장하고 DB에는 URL 또는 key만 둔다. 저장 방식은 이미지 업로드 인프라 작업에서 확정한다.
