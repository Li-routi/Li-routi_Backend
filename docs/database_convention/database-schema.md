# 데이터 모델 가이드

> 기준: 제공된 DDL을 바탕으로 검토한 목표 스키마. 이 문서는 AI와 개발자가 도메인 모델을 빠르게 파악하고 일관된 구현 결정을 내리기 위한 가이드다. **실제로 실행되는 스키마는 `src/main/resources/db/migration`의 Flyway 마이그레이션이며, 이 문서와 어긋나면 마이그레이션이 사실이다.** 아래 "스키마 변경과 데이터 시드"를 먼저 읽을 것.

## 먼저 알아둘 규칙

- 모든 테이블의 기본 키는 `BIGINT id`다. 제공된 DDL에는 자동 생성 전략이 명시되어 있지 않다.
- `DATETIME(6)`은 마이크로초 단위 시각이고, `DATE`는 날짜만 저장한다. 날짜 기반 주기 계산에는 `DATE`를, 실제 이벤트 시각에는 `DATETIME(6)`을 사용한다.
- `deleted_at`이 있는 테이블은 소프트 삭제 대상이다. 일반 조회에서는 반드시 `deleted_at IS NULL`인 데이터만 다룬다. 물리 삭제는 외래 키와 이력 보존에 미치는 영향을 검토한 뒤에만 수행한다.
- `active`는 현재 사용 가능한 마스터/루틴인지 나타낸다. 소프트 삭제와 다르므로 `active = false` 레코드는 관리·이력 화면에서 필요할 수 있다.
- `notification_enabled`, `subscription_enabled`, `auto_delivery_enabled`, `onboarding_completed`는 `TINYINT(1)` 불리언 플래그다.
- 외래 키에는 삭제/수정 전파 규칙이 없다. 부모 레코드를 물리 삭제하면 참조 무결성 오류가 발생할 수 있다.

## 스키마 변경과 데이터 시드

스키마와 마스터 데이터는 **Flyway로 코드에서 관리한다**(#46). 운영 DB에 직접 `ALTER`나 `INSERT`를 치지 않는다.

```text
src/main/resources/db/
├── migration/                     # 모든 환경에 적용
│   ├── V1__baseline.sql           # 구조 — 순서대로 한 번만 실행, 이력이 남는다
│   └── R__seed_challenge.sql      # 운영 마스터 — 내용이 바뀌면 다시 실행된다
└── dummy/                         # local 프로파일에서만 적용
    └── R__dummy_local_data.sql    # 개발용 회원·참여·인증
```

### 두 가지 마이그레이션

- **`V__`(versioned)** — 테이블·컬럼·인덱스 변경. 각 파일은 **한 번만** 실행되고 `flyway_schema_history`에 기록된다. **이미 적용된 파일은 절대 수정하지 않는다.** 체크섬이 어긋나 다음 부팅이 실패한다. 변경이 필요하면 새 파일을 추가한다. 파일명 규칙은 아래 [버전은 타임스탬프로 쓴다](#버전은-타임스탬프로-쓴다)를 볼 것.
  > **적용 순서가 곧 버전 순서는 아니다.** `out-of-order`를 켜 두었기 때문에, 나중에 머지된 낮은 버전이 이미 적용된 높은 버전 뒤에 실행될 수 있다. 이유는 [`out-of-order`를 켜야 동작한다](#out-of-order를-켜야-동작한다)에 있다.
- **`R__`(repeatable)** — 파일 내용이 바뀌면 다시 실행된다. 항상 `V__`가 모두 끝난 뒤에 돈다. 마스터 데이터를 여기에 두는 이유가 이것이다. 구조를 바꾸면서(`V2` 추가) 시드 파일을 함께 고치면 **구조 → 데이터 순서로 자동 재적용된다.** 내용은 얼마든지 고쳐도 되지만 **한 번 적용된 파일의 이름은 바꾸지 않는다.** `R__`는 파일명에서 뽑은 description으로 이력을 추적하므로, 이름을 바꾸면 이력에 남은 옛 이름이 "로컬에 없는 마이그레이션"이 되어 `Validate failed: Migrations have failed validation`으로 **그 DB의 부팅이 막힌다.** 복구하려면 `flyway repair`가 필요한데 아직 그 수단이 없다(#48). 파일 삭제도 같은 이유로 막힌다.

### 버전은 타임스탬프로 쓴다

```text
V20260806143022__soft_delete_group_routine.sql
 └────┬─────┘   └──────────┬──────────┘
  yyyyMMddHHmmss(KST)      설명 — 빼지 않는다
```

파일을 만드는 시각에서 번호를 뽑는다. 초 단위면 충분하다 — 같은 초에 두 사람이 파일을 만들 확률은 사실상 0이다.

#### 왜 순번을 버렸나

**순번은 번호를 파일 만들 때 고르는데 확정은 머지될 때 된다.** 그래서 두 사람이 같은 번호를 집는 일이 반복됐다.

```text
브랜치 A   V14__soft_delete_group_routine.sql
브랜치 B   V14__add_profile_image_key_to_member.sql
develop    V1 ~ V13          ← 둘 다 여기서 갈라져 나왔다
```

**둘 다 잘못한 것이 없다.** 갈라져 나올 때 최대 번호가 `V13`이었으니 각자 `V14`를 고른 것이 맞고, 상대의 `V14`는 아직 PR 상태라 볼 수가 없다.

문제는 **중복이 `브랜치 ∪ develop`에만 생기는데 그 합집합을 보는 장치가 없다**는 것이다. git은 파일명이 달라 충돌로 보지 않고, CI 중복 검사는 `on: push`라 자기 브랜치 트리만 보며(각 브랜치엔 진짜로 하나뿐이다), 브랜치 보호는 `strict: false`라 머지 전 최신화를 강제하지 않는다.

타임스탬프는 번호를 **만든 시각**에서 뽑으므로 이 다툼 자체가 없어진다.

#### 설명(`__` 뒤)은 빼지 않는다

Flyway가 그 값을 `flyway_schema_history.description`에 저장한다. **장애 났을 때 실제로 들여다보는 표다.**

```text
version         description                  success
20260806143022  soft delete group routine    1
```

여기가 비면 이력 조회도 디렉터리 목록도 전부 14자리 숫자만 남는다. **파일 안 주석은 이 표에도 목록에도 나오지 않는다.** 파일명은 *무엇을*, 주석은 *왜*를 담는 것으로 나눈다.

#### `out-of-order`를 켜야 동작한다

**타임스탬프만 도입하고 이 설정을 빼면 부팅이 막힌다.** 순번일 때는 나중에 머지하는 쪽이 번호를 올려 **적용 순서가 늘 오름차순**이었는데, 타임스탬프는 그 보장이 없다.

```text
8/06 A 생성 → 8/07 B 생성 → B 먼저 머지(적용) → 8/08 A 머지
                                                  ↑ 적용된 최대 버전보다 낮다
```

`spring.flyway.out-of-order: true`가 없으면 Flyway가 A를 적용하지 않고 검증에서 실패한다. 조용히 건너뛰더라도 `ddl-auto: validate`라 **엔티티의 컬럼이 DB에 없어 어차피 부팅이 막힌다.**

켜서 잃는 것은 "적용 순서 = 버전 순서"라는 성질이다. 우리 마이그레이션은 테이블·컬럼 추가가 대부분이라 순서가 바뀌어도 결과가 같지만, **앞선 마이그레이션이 만든 것에 의존하는 변경**을 쓸 때는 그 전제가 깨질 수 있다.

#### 기존 `V1`~`V13`은 그대로 둔다

`20260806143022 > 13`이라 새 파일이 자연히 뒤에 온다. **이미 적용된 파일은 이름을 바꾸면 체크섬이 어긋나 부팅이 막히므로** 손대지 않는다. 두 형식이 섞여 있는 것이 정상이다.

### 마스터 데이터 규칙

`challenge`처럼 앱이 제공하는 마스터 데이터는 **저장소의 시드 파일이 단일 진실 공급원**이다. 운영 DB에서 값을 직접 고쳐도 다음 배포에 시드 값으로 되돌아간다. 두 가지를 지킨다.

1. **`id`를 고정한다.** auto increment에 맡기면 재적용 때 다른 `id`가 생겨, `member_challenge`가 참조하던 챌린지가 바뀐다.
2. **`INSERT ... ON DUPLICATE KEY UPDATE`(upsert)로 쓴다.** 매 배포마다 실행되므로 멱등해야 한다. `INSERT IGNORE`는 첫 삽입만 하고 이후 값 변경이 반영되지 않아 쓰지 않는다.
3. **내리고 싶으면 행을 지우지 말고 `active = FALSE`로 바꾼다.** upsert는 추가·수정만 하므로 **시드에서 줄을 지워도 DB에서는 사라지지 않는다.** 지운 채로 두면 "저장소가 진실 공급원"이라는 약속이 그 행에 대해서만 깨진다. 마스터 데이터는 원래 소프트 삭제 대신 `active`로 노출을 제어하므로(아래 `challenge` 절), `active = FALSE` 행으로 남기는 것이 이 규칙과도 맞는다. `member_challenge`가 이미 참조 중인 챌린지를 물리 삭제할 수 없다는 점에서도 그렇다.

로컬 더미는 `db/dummy`에 두고 `id`는 9000번대를 쓴다. 운영 마스터가 쓸 낮은 번호대와 겹치지 않게 하기 위해서다. 테스트(`test` 프로파일)에는 더미를 넣지 않는다 — 테스트는 각자 필요한 데이터를 직접 만들고, 고정 `id` 행이 섞이면 건수 검증이 흔들린다.

더미가 운영에 새지 않도록 프로파일 설정에만 기대지 않는다. **`bootJar`가 `db/dummy`를 배포 산출물에서 제외하므로, 운영 이미지에는 더미 파일 자체가 없다.** 프로파일을 잘못 줘도 넣을 것이 없다(Flyway는 없는 위치를 경고만 하고 넘어간다).

### `R__`은 알파벳 순으로 실행된다

여러 `R__` 파일이 있으면 **description(파일명에서 `R__`과 `.sql`을 뺀 부분)의 알파벳 순**으로 실행된다. 버전 번호가 없으므로 실행 순서를 이름에 의존한다.

그래서 **서로 의존하는 시드는 한 파일에 몰아서 쓴다.** 예를 들어 회원을 넣는 파일과 그 회원의 참여를 넣는 파일을 나누면, 이름 순서에 따라 참여가 먼저 돌아 외래 키 위반이 난다. 굳이 나눠야 하면 `R__seed_01_member.sql`, `R__seed_02_participation.sql`처럼 이름에 순번을 넣어 순서를 고정한다.

### 롤백은 앞으로만 간다

**배포 롤백과 DB 롤백은 대칭이 아니다.** 앱은 `APP_IMAGE_TAG`를 이전 커밋 sha로 되돌리면 그 버전으로 돌아가지만, **스키마는 되돌아가지 않는다.** Flyway 커뮤니티 버전에는 `undo`가 없다.

그래서 컬럼을 지우거나 타입을 바꾸는 마이그레이션을 배포한 뒤 앱만 롤백하면, **옛 앱 + 새 스키마** 조합이 되어 깨진다. 되돌리려면 반대 방향 마이그레이션(`V3__revert_...sql`)을 새로 써서 앞으로 배포한다.

여기서 나오는 실무 규칙이 하나 있다. **파괴적 변경(컬럼 삭제·이름 변경)은 한 번에 하지 않고 두 배포로 나눈다.** 먼저 새 컬럼을 추가하고 코드가 양쪽을 읽게 한 뒤, 다음 배포에서 옛 컬럼을 지운다. 그래야 중간 어느 시점에 롤백해도 앱이 살아 있다.

### `ddl-auto`는 `validate`다

Hibernate는 스키마를 만들지 않고 **엔티티와 실제 스키마가 맞는지 검사만 한다.** 어긋나면 부팅이 실패한다. 예전처럼 `update`로 두면 컬럼 추가만 조용히 반영하고 삭제·타입 변경은 무시해서, 엔티티와 운영 스키마가 아무 에러 없이 벌어진다.

즉 **엔티티를 고쳤는데 마이그레이션을 안 쓰면 앱이 안 뜬다.** 이건 버그가 아니라 의도된 게이트다.

### 기존 DB는 어떻게 되나

Flyway 도입 전부터 쓰던 DB(개인 로컬·운영)에는 이력 테이블이 없다. `baseline-on-migrate: true`, `baseline-version: 0`으로 두어 **기존 DB에서도 `V1`이 실행된다.** `V1`은 `CREATE TABLE IF NOT EXISTS`라 이미 있는 테이블은 건드리지 않고 없는 것만 채운다. 기존 데이터도 그대로 보존된다. 그래서 도입 시 팀원이 로컬 DB를 지울 필요가 없다.

단, `IF NOT EXISTS`는 "테이블이 통째로 없는" 경우만 메운다. 이미 있는 테이블의 컬럼이 어긋난 것은 `validate`가 부팅 시점에 잡는다. 그때는 로컬을 `task docker-reset`으로 초기화하는 게 가장 빠르다.

## 도메인 모델

### 회원이 소유하는 데이터

`member`가 사용자 계정의 루트다. 회원이 직접 소유하는 데이터는 다음과 같다.

- `member_consumable`: 회원이 추적하는 개별 소모품
- `member_housework_routine`: 회원별 집안일 주기
- `member_routine`: 회원이 등록한 개인 루틴
- `member_challenge`: 회원별 챌린지 참여 상태와 연속 참여일
- `member_order`: 회원 주문
- `notification`: 회원에게 전달할 알림

회원 탈퇴 또는 삭제 기능을 구현할 때는 위 데이터를 포함한 소프트 삭제 범위와 접근 차단 정책을 함께 정의해야 한다. 현재 DDL만으로는 연쇄 소프트 삭제 규칙이 정해져 있지 않다.

**단, `member_challenge`는 소프트 삭제 대상이 아니다.** 이 테이블에는 `deleted_at`이 없다. 회원이 탈퇴하면 `member.is_active`와 `member.deleted_at`으로 접근이 차단되므로, 참여 데이터를 별도로 손대지 않고 그대로 둔다. 재가입이나 계정 복구 시 참여 이력이 보존된다.

`member_challenge.active`는 **회원 탈퇴가 아니라 "그 챌린지를 그만두었다"는 뜻이다.** 두 개념을 같은 컬럼으로 표현하지 않는다. 회원 탈퇴를 `active = false`로 기록하면, 계정을 복구할 때 회원이 스스로 그만둔 챌린지와 탈퇴 때문에 꺼진 챌린지를 구분할 수 없게 된다.

### 마스터 데이터

- `consumable_category`: 소모품 분류와 기본 사용 주기
- `housework_template`: 공통 집안일 템플릿과 기본 주기
- `challenge`: 앱이 제공하는 챌린지. 회원이 직접 생성할 수 없다.
- `chat_emoticon`: 앱이 제공하는 그룹 채팅 이모티콘 자산 메타데이터. 회원이 직접 등록할 수 없다.
- `routine_template`: 카테고리별 기본 제공 루틴
- `routine_category`: 개인 루틴 카테고리. 앱이 제공하는 고정 카테고리와 회원이 추가한 사용자 카테고리를 함께 담는다(`member_id`로 구분).
- `group_routine_category`: 그룹 루틴 카테고리. 앱이 제공하는 고정 카테고리와 그룹별 사용자 카테고리를 함께 담는다(`group_id`로 구분).
- `product`: 판매 상품. 선택적으로 `consumable_category`에 속한다.
- `notification_type`: 알림 코드, 제목·본문 템플릿, 대상 유형

마스터 데이터는 `active`로 노출 여부를 제어한다. 이미 사용된 템플릿, 카테고리, 상품은 이력 데이터가 참조할 수 있으므로 비활성화와 소프트 삭제의 사용 목적을 구분한다.

### 이력 및 거래 데이터

- `member_housework_completion_log`: 특정 집안일 루틴의 완료 이력. `canceled_at`이 있으면 완료 처리가 취소된 이력이다.
- `challenge_verification`: 챌린지 참여의 일자별 인증 이력. 하루에 한 건만 존재한다.
- `challenge_verification_report`: 인증에 대한 신고. 신고자 본인의 피드에서 즉시 가리고, 임계값만큼 쌓이면 전체 회원에게 가린다.
- `consumable_purchase_log`: 소모품 구매·보충 이력. 주문과 상품 연결은 선택 사항이므로 수동 등록도 가능하다.
- `order_detail`: 주문에 포함된 상품과 주문 당시 가격 스냅샷. 상품의 현재 가격이 바뀌어도 `unit_price`, `total_price`는 주문 이력으로 유지한다.
- `payment`: 주문의 결제 시도·승인·실패 정보. 현 스키마상 한 주문에 복수 결제 레코드를 둘 수 있다.
- `group_chat_message`: 그룹에 보존되는 채팅 메시지 이력. 탈퇴 회원이 작성한 메시지도 유지한다.

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
- `member_routine`은 `member`와 `routine_category`를 반드시 참조하고, `routine_template`은 선택적으로 참조한다. 집안일과 같은 구조다 — 템플릿 없이 회원이 직접 만든 루틴도 허용한다.
- `routine_category`는 `member`를 선택적으로 참조한다. 참조가 없으면 앱이 제공하는 고정 카테고리다. 조회할 때 항상 "고정 + 요청 회원의 것"으로 범위를 좁혀야 한다.
- `group_routine_category`는 `member_group`을 선택적으로 참조한다. 참조가 없으면 앱이 제공하는 고정 그룹 카테고리다. 조회할 때 항상 "고정 + 요청 그룹의 것"으로 범위를 좁혀야 한다.
- `routine_template`은 반드시 하나의 `routine_category`에 속한다. 고정 카테고리에만 붙는다.
- `group_chat_message`는 `member_group`과 발신자 `member`를 반드시 참조하고, 이모티콘 메시지만 `chat_emoticon`을 선택적으로 참조한다.
- `group_chat_read`는 `member_group`과 `member`를 반드시 참조하고, 마지막으로 읽은 `group_chat_message`를 선택적으로 참조한다.

## 주요 비즈니스 흐름

### 소모품 관리

회원은 `member_consumable`에 소모품명, 최근 구매일, 평균 사용 일수를 기록한다. `expected_depletion_date`는 알림과 자동 배송 판단에 쓰일 수 있는 파생 값이다. 구매가 발생하면 `consumable_purchase_log`를 남기고, 필요에 따라 해당 소모품의 최근 구매일·예상 소진일을 갱신한다.

### 집안일 관리

템플릿을 기반으로 하거나 직접 만든 `member_housework_routine`에 반복 주기와 다음 예정일을 둔다. 완료하면 `member_housework_completion_log`를 생성하고, 루틴의 `last_performed_date` 및 `next_due_date`를 갱신한다. 완료 취소는 로그를 지우기보다 `canceled_at`을 기록하는 방식으로 해석한다.

### 챌린지

`challenge`는 앱이 제공하는 마스터 데이터이고, 회원은 `member_challenge`로 참여한다. 인증하면 `challenge_verification`을 생성하고, 같은 트랜잭션에서 참여의 `current_streak`과 `last_verified_date`를 갱신한다.

**인증 INSERT와 연속 참여일 갱신은 반드시 하나의 트랜잭션이어야 한다.** 이 조건이 지켜지면 **같은 회차 안에서는** 연속 참여일 갱신에 별도의 락(낙관적 락)이 필요하지 않다. `UNIQUE(member_challenge_id, participation_round, period_start_date)` 덕분에, 같은 회원이 같은 구간에 인증을 동시에 두 번 요청하면(= 동일한 복합 유니크 키 값으로 INSERT를 시도하면) **둘 중 하나만 성공하고 나머지는 유니크 제약 위반으로 실패하기 때문이다.** 실패한 요청은 같은 트랜잭션의 연속 참여일 갱신도 함께 롤백한다. 따라서 갱신이 덮어써져 사라지는(lost update) 경로가 없다. **반대로 두 작업을 다른 트랜잭션으로 분리하면 이 보장이 깨지므로 그렇게 구현하지 않는다.**

> ⚠️ **이 보장은 회차 안에서만이다.** 유니크 키에 `participation_round` 가 들어 있어, 재참여로 회차가 오르면 같은 구간이라도 키가 달라 DB 가 막지 않는다. 그래서 저장 경로는 **참여 행을 `SELECT ... FOR UPDATE` 로 잡은 뒤** 회차를 뺀 조회로 확인한다. 위 문장의 "락이 필요 없다"를 그 잠금까지 없애도 된다는 뜻으로 읽으면 안 된다 — 아래 [주기 1회는 회차를 넘는다]를 볼 것.

이 보장에는 전제가 하나 더 있다. **유니크 제약 위반 예외를 삼키면 안 된다.** InnoDB는 중복 키 오류가 발생해도 해당 문장만 되돌릴 뿐 트랜잭션을 자동으로 롤백하지 않는다. 따라서 "이미 인증했으니 무시하고 넘어가자"는 의도로 예외를 잡아 처리를 계속하면, **이미 실행된 연속 참여일 갱신이 그대로 커밋되어 값이 두 번 증가한다.** 예외는 그대로 전파시켜 트랜잭션 전체를 롤백하고, 중복 인증은 사용자에게 명시적인 에러로 응답한다.

연속 참여일은 인증 시점에 다음 규칙으로 계산한다.

**단위는 주기다** — `DAILY`면 연속 며칠, `WEEKLY`면 연속 몇 주, `MONTHLY`면 연속 몇 달이다. 아래에서 "구간"은 그 주기의 한 단위를 뜻한다.

- `last_verified_date`가 **직전 구간**이면 `current_streak`을 1 증가시킨다.
- `last_verified_date`가 **이번 구간**이면 이미 인증한 상태이므로 갱신하지 않는다. 주기 1회 제약에 걸린다.
- 그보다 오래되었거나 값이 없으면 `current_streak`을 1로 초기화한다.

구간 비교는 `RoutineCycle.isSamePeriod` / `isPreviousPeriod` 가 한다. **컬럼을 늘리지 않는다** — `last_verified_date` 하나로 계산된다.

**연속이 끊기는 시점에는 어떤 이벤트도 발생하지 않는다.** 회원이 두 구간을 쉬어도 이를 감지해 `current_streak`을 0으로 바꿔주는 주체가 없다. 따라서 배치로 초기화하지 않고, **조회 시점에 `last_verified_date`가 이번 구간도 직전 구간도 아니면 연속 참여일을 0으로 판정한다.** 저장된 값과 화면에 표시되는 값이 다를 수 있다는 뜻이므로, 조회 로직에서 반드시 이 판정을 거친다.

날짜 기준은 KST 자정이다. `verified_date`는 `DATE`, `verified_at`은 실제 인증 시각(`DATETIME(6)`)이다.

**한 구간에 한 번 내면 끝이다. 유일한 예외는 본인이 지운 경우다.**

| 상황 | 결과 |
| --- | --- |
| 그 구간에 인증이 있다 | **409** — `DAILY`·주간·월간 모두 같다 |
| 보류(`PENDING`) 상태다 | **409** — 심사 중이어도 이미 낸 것이다 |
| 탈퇴 후 재참여했다 | **409** — 회차를 넘어 적용된다 |
| **본인이 지웠다** | **다시 낼 수 있다** — 모든 주기 동일 |

**주기로 가르지 않는다.** 주가 지나지 않았으면 주간도 하루와 똑같이 "이미 낸 것"이다. 구간의 길이만 다르고 규칙은 하나다.

**"올리고 기록만 챙긴 뒤 지우기"를 막는 역할은 리워드 회수가 맡는다**(재화 이슈). 저장 경로에서 막지 않는다 — 회수는 시간과 무관하게 적용되는 반면, 저장에서 막으면 정당하게 사진을 바꾸려는 사람만 다친다.

> ⚠️ **회수는 아직 구현되지 않았다.** 그때까지 이 규칙은 "막는 장치 없이 열려 있는" 상태다. 재화 이슈가 들어오기 전까지의 공백을 인지하고 진행하기로 했다 — 지금 사용자 규모에서 악용 여지가 크지 않다고 봤다.

되살릴 때는 새 행을 만들지 않고 **지워진 행을 되살린다**(`deleted_at` 을 비운다). 구간에 한 행이라는 사실이 유지되므로 유니크 제약과 부딪히지 않고, 그 인증을 참조하던 좋아요·신고도 그대로 남는다.

> **지운 행이 지난 회차 것이면 되살리지 않고 새로 만든다.** 되살리면 회차가 옛 값으로 남아 "이번 참여의 기록"이라는 뜻이 어긋난다. 유니크 키에 회차가 들어 있어 새 행을 만들어도 부딪히지 않는다.

##### 신고로 가려진 글은 그 구간이 닫힌 채로 끝난다

**의도한 것이다.** 두 규칙이 겹쳐서 그렇게 된다.

| | |
| --- | --- |
| 가려진 글은 지울 수 없다 | 지워서 신고 누적을 회피하는 길을 막는다 |
| 지우지 않은 인증은 구간을 점유한다 | 위 표의 첫 줄 |

그래서 **가려진 글이 있는 구간에는 아무것도 할 수 없다.** 삭제는 404, 다시 인증하면 409다.

가려짐은 제재이므로 그 구간을 소진한 것으로 본다. **다시 열어 주면 신고를 받은 사람이 사진만 바꿔 계속 낼 수 있고**, 그러면 숨김이 제재로서 아무 힘이 없다.

> 예전에는 `DAILY` 에 한해 덮어쓸 수 있었다 — 사진은 바뀌지만 `hidden_at` 은 그대로라 여전히 가려진 채였다. 재인증을 삭제한 경우로 좁히면서 그 길도 함께 닫혔다. 없애려고 한 것은 아니고, 닫히는 편이 제재와 일관돼 그대로 두었다.

**사용자에게는 버튼이 "완료" 로 보인다.** 가려진 글도 인증한 것으로 세기 때문이다(숨김은 노출만 막을 뿐 수행 기록이 아니다). 즉 화면상으로는 그냥 그 구간을 마친 상태이고, 막다른 길처럼 보이지 않는다. 하루에 한 행이라는 사실이 유지되므로 유니크 제약과 충돌하지 않고, 그 인증을 참조하는 신고 데이터도 그대로 남는다. 어제 이전의 인증은 수정할 수 없으므로 연속 참여일을 소급해 재계산하는 경우는 없다. 집안일의 `canceled_at`에 해당하는 컬럼을 두지 않는 이유다.

챌린지를 그만두면 `member_challenge.active`를 `false`로 둔다. 다시 참여하면 같은 행을 재활성화하고, 연속 참여일을 0으로 초기화하며 **참여 회차(`participation_round`)를 1 증가시킨다.** 참여 이력을 새 행으로 쌓지 않으므로 `(member_id, challenge_id)` 유니크 제약을 유지할 수 있다.

회차를 두는 이유는 **지난 참여의 인증을 현재 참여와 분리**하기 위해서다. 회차가 없으면 이탈 전 기록이 현재 참여 상태에 섞인다.

**다만 회차가 하루 1회를 우회하는 수단이 되지는 않는다.** 도입 당시에는 "같은 날 이탈 후 재참여하면 다시 인증할 수 있다"가 의도였는데, 그 결과 나가기/들어오기를 반복해 하루에 인증 게시글을 얼마든지 올릴 수 있었다. 지금은 막는다 — 아래 [주기 1회는 회차를 넘는다]를 볼 것.

**연속 참여일만 현재 회차 기준이다**(재참여하면 0에서 다시 시작한다). 인증 목록과 오늘 완료 여부는 회차를 보지 않는다.

신고는 인증을 삭제하지 않는다. 효과가 두 단계다 — 신고 즉시 **신고자 본인에게만** 제외되고, 신고가 임계값만큼 쌓이면 **전체 회원에게** 가려진다(`hidden_at`). 임계값에 못 미치는 동안에는 다른 회원에게 그대로 노출된다.

### 주문과 결제

`member_order`는 배송·수령 정보와 총액을 보관하는 주문 헤더이며, 구매 품목은 `order_detail`에 저장한다. 결제 요청부터 승인 또는 실패까지의 상태와 외부 거래 식별자는 `payment`에 저장한다. 결제 실패 이력을 보존할 수 있으므로 단순히 주문당 결제 1건이라고 가정하지 않는다.

### 알림

`notification_type`은 재사용 가능한 알림 정책·문구 템플릿이고, `notification`은 회원에게 예약된 개별 발송 건이다. `scheduled_at`, `sent_at`, `read_at`은 각각 예약, 발송, 읽음 상태를 나타낸다.

## 테이블별 필드 참고

아래는 각 테이블의 주요 필드다. 구현 시에는 이 문서의 의미 해석을 우선하고, 타입·제약은 실제 마이그레이션 또는 엔티티 정의와 대조한다.

## 업적 테이블

### `achievement`

앱이 제공하는 업적 마스터 데이터다. 업적 자체의 노출 여부는 `active`로 관리하며,
회원별 달성·수령 상태는 `member_achievement`에 저장한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| code | VARCHAR(20) | N | 업적의 안정적인 논리 식별자. 유니크 |
| category | VARCHAR(10) | N | 업적 카테고리 |
| name | VARCHAR(50) | N | 업적명 |
| condition_desc | VARCHAR(200) | N | 조건 설명 |
| progress_type | VARCHAR(30) | N | 진행도 계산 방식 |
| target_count | INT | Y | 단일 조건 업적의 목표치 |
| condition_key | VARCHAR(40) | Y | 단일 조건 업적의 이벤트 키 |
| topaz_reward | INT | N | 지급 토파즈 |
| badge_yn | BOOLEAN | N | 뱃지 보상 여부. 이미지 존재 여부와는 별개 |
| badge_image_key | VARCHAR(500) | Y | public-read S3의 업적 뱃지 object key. 전체 URL을 저장하지 않음 |
| limited_outfit_yn | BOOLEAN | N | 한정 의상 보상 여부 |
| sort_order | INT | N | 회원용 목록 정렬 순서 |
| active | BOOLEAN | N | 회원용 목록 노출 여부 |

`badge_image_key`는 업적당 현재 사용 이미지 하나를 가리킨다. 이미지 등록 전 기존 업적을
지원하기 위해 nullable로 두며, 이미지를 교체할 때는 기존 object를 덮어쓰지 않고 새 key를
저장한다. 회원용 응답 URL은 조회 시 `MediaPurpose.ACHIEVEMENT_BADGE`의 public URL로
조립한다.

업적 뱃지 object는 다음 규칙을 따른다.

```text
achievement-badges/{yyyy}/{MM}/{dd}/{UUID}.{extension}
```

업적 key를 참조하는 원천은 `AchievementMediaReferenceSource`로 등록해 미참조 media
cleanup이 현재 DB 참조를 보존하도록 한다. 기존 업적 테이블은 수정하지 않고
`V20260813143702__add_achievement_badge_image_key.sql` forward migration으로 컬럼을 추가한다.

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

> **이 절의 표는 실물 배송 쇼핑용이다.** `member_order` 에 배송지가 있고 `product` 가 소모품 분류를 참조한다. **아바타 아이템은 이 표를 쓰지 않는다** — 배송지도 소모품 분류도 없고 결제 수단이 현금이 아니라 재화다. 아래 [아바타 아이템(상점) 테이블] 을 볼 것.

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

## 개인 루틴 테이블

개인 루틴은 회원 혼자 수행하는 루틴이다. 그룹 루틴과 카테고리 테이블을 공유하지 않으며,
요일별 시간 범위 대신 루틴 단위 마감 시각을 갖는다.

### `routine_category`

개인 루틴 카테고리 마스터다. 그룹 루틴은 별도의 `group_routine_category`를 사용한다.

`member_id`가 두 종류를 가른다. `NULL`이면 앱이 제공하는 **고정 카테고리**이고
(운동, 건강, 자기계발, 생활정리, 마음관리, 취미 — `R__seed_routine.sql`이 관리),
값이 있으면 그 회원만 쓰는 **사용자 카테고리**다. 회원당 최대 5개까지 추가할 수 있다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | Y | 소유 회원, `member.id` FK. `NULL`이면 고정 카테고리 |
| name | VARCHAR(100) | N | 카테고리 이름 |
| color | ENUM | Y | 색상 칩. 고정 카테고리와 "색 없음"은 `NULL` |
| display_order | INT | N | 노출 순서. 사용자 카테고리는 0이라 생성 순서(id)로 정렬된다 |
| active | TINYINT(1) | N | 사용 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_routine_category_member_name` (`member_id`, `name`)

MySQL은 유니크 키에서 `NULL`을 서로 다른 값으로 취급하므로 이 제약은 고정 카테고리끼리의 이름
중복을 막지 못한다. 고정 카테고리는 시드가 고정 id로만 넣으므로 실제로 중복이 생길 경로가 없다.
"사용자가 고정 카테고리와 같은 이름을 만들 수 없다"는 규칙도 소유자가 달라 이 제약에 걸리지
않으므로 애플리케이션에서 검증한다.

### `routine_template`

카테고리마다 미리 보여 주는 기본 제공 루틴이다. 회원이 만들 수 없는 마스터 데이터이며
`R__seed_routine.sql`이 단일 진실 공급원이다. 시간·요일 컬럼이 없다 — 마감 시각과 반복 요일은
회원이 고르는 값이라 `member_routine`에 있다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| category_id | BIGINT | N | 노출 카테고리, `routine_category.id` FK |
| name | VARCHAR(20) | N | 기본 루틴 이름 |
| display_order | INT | N | 카테고리 안에서의 노출 순서 |
| active | TINYINT(1) | N | 노출 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_routine_template_category_name` (`category_id`, `name`)

### `member_routine`

회원이 등록한 개인 루틴이다. 활성 루틴은 회원당 최대 30개다.

`routine_template_id`가 있으면 기본 제공 루틴을 고른 것이고, `NULL`이면 직접 추가했거나
기본 루틴의 이름을 바꾼 것이다. **기본 루틴의 이름을 바꾸면 원본 선택이 해제되어** 같은
카테고리의 사용자 루틴이 된다(`MemberRoutine` 생성자가 참조를 뗀다). 사용자 루틴끼리는
같은 이름을 허용하므로 이름에 유니크 제약이 없다.

`deleted_at`이 없다. 삭제 API가 아직 없어 소프트 삭제 정책이 정해지지 않았고,
활성 루틴 개수는 `active`로 센다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 소유 회원, `member.id` FK |
| category_id | BIGINT | N | 소속 카테고리, `routine_category.id` FK |
| routine_template_id | BIGINT | Y | 원본 기본 루틴, `routine_template.id` FK |
| name | VARCHAR(20) | N | 루틴 이름. 앞뒤 공백 제거 후 1~20자 |
| end_time | TIME | N | 마감 시각. 기본값 23:59 |
| alarm_time | TIME | Y | 알람 시각. "없음"이면 `NULL` |
| active | TINYINT(1) | N | 활성 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_member_routine_member_template` (`member_id`, `routine_template_id`)
— 같은 기본 루틴을 두 번 고를 수 없다. `NULL`인 직접 추가 루틴은 이 제약에 걸리지 않는다.

인덱스: `idx_member_routine_member_active` (`member_id`, `active`)

### `member_routine_schedule`

개인 루틴이 반복되는 요일이다. 최소 1개이며 기본값은 매일이다. 그룹 루틴의 일정과 달리
요일마다 시간 범위를 두지 않는다 — 마감 시각은 루틴 단위로 한 번만 정한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_routine_id | BIGINT | N | 대상 루틴, `member_routine.id` FK |
| repeat_day | ENUM | N | 반복 요일(`MONDAY`~`SUNDAY`) |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_member_routine_schedule_day` (`member_routine_id`, `repeat_day`)

## 그룹 루틴 카테고리 테이블

### `group_routine_category`

그룹 루틴 전용 카테고리다. 개인 `routine_category`와 필드와 색상 정책은 같지만 소유자가
회원이 아니라 그룹이다. `group_id`가 `NULL`이면 앱이 제공하는 고정 카테고리이고, 값이 있으면
해당 그룹만 사용하는 사용자 카테고리다. 고정 목록은 `R__seed_group_routine_category.sql`이
고정 id 1~6으로 관리한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| group_id | BIGINT | Y | 소유 그룹, `member_group.id` FK. `NULL`이면 고정 카테고리 |
| name | VARCHAR(100) | N | 카테고리 이름 |
| color | ENUM | Y | 색상 칩. 고정 카테고리와 "색 없음"은 `NULL` |
| display_order | INT | N | 노출 순서 |
| active | TINYINT(1) | N | 사용 여부 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_group_routine_category_group_name` (`group_id`, `name`)

인덱스: `idx_group_routine_category_group_active` (`group_id`, `active`)

`group_routine.group_routine_category_id`는 이 테이블을 참조한다. 고정 카테고리 또는 루틴과
같은 그룹의 카테고리만 연결할 수 있으며, 소유 범위 검증은 애플리케이션에서 추가로 수행한다.
그룹 루틴 카테고리 전환은 MySQL DDL의 부분 적용 위험을 줄이기 위해 단계별로 수행한다.

- V10: `group_routine_category` 테이블 생성
- V11: 기본·그룹별 카테고리 backfill 및 `group_routine_category_id` 매핑
- V12: 신규 FK와 NOT NULL 제약 적용, 기존 `category_id` nullable 전환
- V13: `invite_code_expires_at` 기존 데이터 보정 및 NOT NULL 적용

기존 `category_id`와 개인 카테고리 FK는 운영 데이터 검증이 끝날 때까지 유지하며, 후속
마이그레이션에서 제거한다.

## 그룹 채팅 테이블

그룹 하나를 채팅방 하나로 사용한다. MySQL의 `group_chat_message`가 메시지의 진실 공급원이며,
WebSocket은 저장된 메시지를 실시간 전달하는 수단이다. 그룹 채팅 테이블은
`V20260806102755__group_chat.sql`에서 생성하고,
`V20260806102756__scope_chat_client_message_id_to_group.sql`에서 재전송 식별자의 유니크 범위를
그룹·발신자 단위로 보정한다.

### `chat_emoticon`

서비스가 관리하는 채팅 이모티콘의 메타데이터다. 실제 이미지 bytes는 private S3에 두고
`asset_key`에는 전체 URL이 아닌 object key를 저장한다. 일반 회원은 이 테이블이나 자산을
직접 등록하지 않는다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키, auto increment |
| code | VARCHAR(100) | N | 클라이언트가 전송하는 안정적인 논리 식별자. 유니크 |
| asset_key | VARCHAR(500) | N | private S3 object key |
| content_type | VARCHAR(30) | N | 현재 허용 값은 `image/png`, `image/jpeg`, `image/webp` |
| animated | BIT(1) | N | 애니메이션 여부. 현재 지원 형식은 모두 `false` |
| active | BIT(1) | N | 신규 메시지에서 선택할 수 있는지 여부 |
| display_order | INT | N | 활성 목록 노출 순서 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_chat_emoticon_code` (`code`)

인덱스: `idx_chat_emoticon_active_order` (`active`, `display_order`)

이미 메시지에서 참조한 이모티콘은 물리 삭제하지 않는다. 신규 전송만 막으려면
`active = false`로 바꾸고, 기존 메시지 조회와 미참조 S3 정리에서는 활성 여부와 관계없이
`asset_key`를 보존한다.

### `group_chat_message`

그룹에 저장되는 서버 기준 메시지다. 발신자와 생성 시각은 인증 정보와 DB에서 결정하며
클라이언트가 보낸 값을 신뢰하지 않는다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키, auto increment. cursor·읽음 위치·broadcast 기준 |
| group_id | BIGINT | N | 채팅방인 `member_group.id` FK |
| sender_id | BIGINT | N | 발신 회원인 `member.id` FK |
| message_type | VARCHAR(20) | N | `TEXT` 또는 `EMOTICON` |
| content | VARCHAR(2000) | Y | TEXT 본문. EMOTICON이면 `NULL` |
| emoticon_id | BIGINT | Y | `chat_emoticon.id` FK. EMOTICON 메시지에서 사용 |
| reply_to_message_id | BIGINT | Y | 같은 그룹의 답장 원본 `group_chat_message.id` FK |
| client_message_id | VARCHAR(100) | N | 클라이언트가 생성하고 재시도 때 재사용하는 식별자 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_group_chat_message_sender_client`
(`group_id`, `sender_id`, `client_message_id`)

인덱스:

- `idx_group_chat_message_group_id_id` (`group_id`, `id`) — 그룹별 ID cursor 조회
- `idx_group_chat_message_group_id_created_at` (`group_id`, `created_at`) — 그룹별 날짜 목록·날짜 범위 조회
- `idx_group_chat_message_sender_id` (`sender_id`) — 발신자 FK 조회와 참조 무결성 지원
- `idx_group_chat_message_reply_to_message_id` (`reply_to_message_id`) — 답장 원본 FK 조회와 참조 무결성 지원

`reply_to_message_id`가 `NULL`이면 일반 메시지이고, 값이 있으면 같은 그룹의 원본 메시지를
가리킨다. 저장 전 Service에서 원본의 그룹 소속을 검증한다.

동일한 그룹·발신자·`client_message_id`의 동시 재전송은 원자적 insert와 위 유니크 키로 한 건만
저장한다. 같은 식별자에 다른 payload가 들어오면 애플리케이션에서 충돌로 거부한다.
메시지 타입별 `content`·`emoticon_id` 조합은 현재 DB CHECK 제약이 아니라 Service 검증으로
보장한다.

메시지에는 `deleted_at`이 없다. 그룹을 나가거나 회원이 탈퇴해도 공동 공간의 기존 메시지는
작성자 이력과 함께 유지하고, 접근은 활성 회원·ACTIVE 그룹 멤버십으로 차단한다. 그룹 자체의
삭제·해체 시 메시지와 읽음 위치를 언제 물리 정리할지는 아직 확정되지 않았으므로 FK cascade를
추가하지 않는다. 정책이 확정되면 기존 migration을 수정하지 않고 새 forward migration으로
반영한다.

### `group_chat_read`

메시지마다 회원별 읽음 행을 만들지 않고, 회원·그룹별 마지막 읽음 위치 하나를 저장한다.
여러 기기에서 요청 순서가 뒤집혀도 더 작은 메시지 ID로 위치를 되돌리지 않는다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키, auto increment |
| group_id | BIGINT | N | 대상 `member_group.id` FK |
| member_id | BIGINT | N | 읽음 위치 소유자인 `member.id` FK |
| last_read_message_id | BIGINT | Y | 마지막으로 읽은 `group_chat_message.id` FK |
| read_at | DATETIME(6) | Y | 마지막 읽음 위치를 전진시킨 시각 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_group_chat_read_group_member` (`group_id`, `member_id`)

인덱스: `idx_group_chat_read_group_message` (`group_id`, `last_read_message_id`)

읽음 위치는 MySQL 조건부 upsert로 생성하거나 더 큰 메시지 ID로만 전진시킨다. 저장 전에 해당
메시지가 요청 그룹에 속하는지 검증한다. 회원 또는 그룹 탈퇴 시 행을 별도로 삭제하는 정책은
현재 없으며, 비활성 멤버에게는 읽음 API 접근을 허용하지 않는다.

## 챌린지 테이블

기존 테이블과 달리, 아래 표에는 유니크 제약과 인덱스를 함께 명시한다. 중복 신고 방지는 애플리케이션 검증만으로는 동시 요청을 막을 수 없으므로 DB 제약으로 보장한다.

**주기 1회 인증은 DB 제약만으로 보장되지 않는다.** 유니크 키에 `participation_round`가 들어 있어 회차가 다르면 같은 구간도 통과하기 때문이다. 그 부분은 저장 경로가 참여 행을 비관 잠금으로 잡은 뒤 확인하는 방식으로 막는다. 자세한 것은 아래 [주기 1회는 회차를 넘는다]를 볼 것.

### `challenge`

앱이 제공하는 챌린지 마스터다. 회원이 직접 생성할 수 없다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| name | VARCHAR(100) | N | 챌린지명 |
| description | VARCHAR(255) | Y | 설명 |
| image_url | VARCHAR(2048) | Y | **운영이 지정한** 대표 이미지. 비어 있으면 인기 인증 사진으로 대체한다(아래 [대표 이미지] 참고) |
| category | VARCHAR(50) | N | 분류. `HEALTH`, `EXERCISE`, `STUDY`, `LIFE`, `HOBBY` |
| routine_cycle | VARCHAR(20) | N | 인증 주기. `DAILY`, `WEEKLY`, `MONTHLY`. 기본값 `DAILY`. 프론트가 매일/매주/매월로 표시 |
| reward | INT | N | 인증 통과 시 지급할 **무료 재화(`TOPAZ`)** 수량. 기본값 `0` |
| active | TINYINT(1) | N | 노출 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `category`는 화면의 필터 칩과 다르다. 칩의 `전체`는 분류가 아니라 **필터 없음**을 뜻한다. 실제 분류는 위 5개다. 목록은 최신순(id 내림차순) 고정이며 정렬 옵션은 두지 않는다.
- 상시 운영이므로 시작일·종료일 컬럼을 두지 않는다.
- **전체(찾아보기) 목록 카드**는 참여자 수·인증 게시글 수·카테고리·루틴 주기를 함께 보여준다. **내 챌린지 목록**은 심플 카드(이미지·제목·설명·카테고리)만 보여준다.
- 참여자 수는 `member_challenge`를 집계해서 구한다(전체 목록 카드·상세에 노출, 내 목록엔 미노출). 인증 게시글 수는 `challenge_verification`을 집계한다. 둘 다 캐시 컬럼을 두지 않는다.
- `routine_cycle`은 인증 주기를 뜻한다. 현재 운영 데이터는 `DAILY` 38 / `WEEKLY` 15 / `MONTHLY` 13이다. **연속 참여일(스트릭)은 주기 단위로 센다** — `DAILY` 면 연속 며칠, `WEEKLY` 면 연속 몇 주, `MONTHLY` 면 연속 몇 달이다. 숫자의 단위만 바뀌고 컬럼은 그대로다(`last_verified_date` 하나로 계산된다). **표시 문구("N일 연속" / "N주 연속")는 클라이언트가 정한다** — 응답에 `routineCycle` 이 실려 있고, 서버가 문구를 만들면 문안 변경이 서버 배포에 묶인다.
- `reward`는 성공 시 지급할 재화 수량을 **보관만** 한다. 실제 지급(적립)과 "챌린지 성공"의 정의는 아직 미구현이며, 회원 재화(지갑) 도메인과 성공 기준이 선행되어야 한다.
- 마스터 데이터이므로 소프트 삭제 대신 `active`로 노출을 제어한다.
- 이름 부분 검색을 지원한다. `LIKE '%키워드%'`는 인덱스를 타지 못하므로, 데이터가 늘어나면 검색 방식을 재검토한다.

#### 대표 이미지 (표지)

응답의 `imageUrl` 은 **한 필드에 두 출처**가 실린다.

1. `challenge.image_url` 이 채워져 있으면 **그것이 이긴다.** 이 컬럼은 원래 운영 표지 자리로 만들었고 그 계획은 살아 있다.
2. 비어 있으면 **그 챌린지에서 좋아요를 가장 많이 받은 인증 사진**을 쓴다.
3. 쓸 인증이 하나도 없으면 `null` 이다. **기본 이미지는 클라이언트가 그린다**(아래 [인증이 없을 때] 참고).

컬럼을 새로 만들지 않은 이유는 이 둘이 화면에서 같은 자리이기 때문이다. 나뉘어 있으면 클라이언트가 "둘 중 뭘 그리나"를 매번 판단해야 하고, 그 판단이 화면마다 갈린다.

**저장하지 않고 조회할 때마다 계산한다.** 좋아요가 바뀌면 표지도 바뀐다 — 캐시 컬럼을 두면 무효화 시점을 따로 정해야 하고, 무엇보다 **글을 지웠을 때 표지가 즉시 내려가지 않는다.** 그 즉시성이 중요하다. 인증 사진이 표지로 쓰이는 데 별도 동의 절차를 두지 않는 대신, **글을 지우면 바로 표지에서 빠지는 것이 사실상의 거부 수단**이다.

**표지에서 빼는 인증 네 가지.** 표지는 참여하지 않은 사람에게도 목록에서 보이므로, 다른 어떤 조회보다 넓게 공개된다.

| 상태 | 왜 빼나 |
| --- | --- |
| 신고 누적 숨김(`hidden_at`) | 피드에서 가린 사진이 표지로 올라가면 가린 의미가 없다 |
| 심사 보류(`review_status = PENDING`) | 심사를 안 지난 사진이 가장 넓게 공개된다. 비공개로 받기로 한 이유가 무너진다 |
| 삭제(`deleted_at`) | 지운 사람의 사진이 표지로 남는다 |
| 탈퇴 회원의 인증 | 다른 집계가 이미 빼는 기준과 맞춘다 |

**조회자별 신고(`notReportedBy`)는 넣지 않는다.** 표지는 모두에게 같은 값이어야 한다 — 사람마다 다르면 배치 조회로 한 번에 뽑을 수도, 나중에 캐시할 수도 없다.

**좋아요 수를 세는 규칙은 피드 정렬과 같다** — 탈퇴 회원이 누른 좋아요는 세지 않는다. 다르게 세면 "표지는 1위인데 화면의 좋아요 수는 2위보다 적은" 상태가 된다.

**동점이면 `id` 내림차순(가장 최근 인증)이다.** 대부분의 챌린지는 좋아요가 전부 0이라 사실상 이 기준이 표지를 정한다. 없으면 **요청마다 표지가 바뀐다.**

**목록과 상세가 같은 함수를 쓴다.** 상세는 `coverImagesByChallengeIds(List.of(id))` 로 배치 조회를 한 건짜리로 부른다. 단건용을 따로 만들면 두 규칙이 갈라져, 목록에서 본 카드와 들어가서 본 사진이 달라진다.

##### 인증이 없을 때 — 기본 이미지는 클라이언트가 그린다

새로 만든 챌린지에는 인증이 없어 표지도 없다. **서버는 `null` 을 내리고, 그 자리를 채우는 것은 클라이언트다.**

서버가 기본 이미지 URL을 내려주지 않는 이유는 셋이다.

1. **`category` 가 이미 응답에 실려 있다.** 클라이언트가 분류별 기본 이미지를 가지면 서버는 아무것도 안 해도 된다. 서버가 매핑을 가지면 그림을 바꿀 때마다 배포가 필요해진다.
2. **이 저장소의 기존 기준과 같다.** enum 을 내려주고 표시는 클라이언트가 정한다 — 스트릭 문구("N일 연속"), 신고 사유 문구가 전부 그렇게 되어 있다.
3. **표지는 목록에 여러 장이 동시에 뜬다.** 번들 자산이면 요청이 0이고 깨진 이미지가 깜빡이지 않는다. 클라이언트는 어차피 이미지 로드 실패 경로를 가져야 하므로 `null` 처리는 추가 부담이 아니다.

**분류 다섯 가지에 각각 기본 이미지 한 장씩**을 두고, 플랫폼끼리 같은 그림을 쓰도록 자산 이름을 합의해 둔다. `HEALTH` · `EXERCISE` · `STUDY` · `LIFE` · `HOBBY` 다.

> ⚠️ **`challenge.image_url` 에 기본 이미지를 넣어서는 안 된다.** 이 컬럼은 폴백 1순위(운영 표지)라, 채워 두면 **인증이 아무리 쌓여도 영원히 그 그림만 보인다.** 기능이 통째로 죽는데 에러는 하나도 나지 않는다. 가장 손이 가는 방법이 정확히 이 지뢰라 여기 적어 둔다. 이 컬럼은 **운영이 특정 챌린지에 표지를 지정할 때만** 쓴다.

**비용은 알고 있다.** 챌린지별 1위를 SQL 한 방으로 뽑으려면 윈도 함수가 필요한데 QueryDSL(JPA)에서는 쓸 수 없어, 후보를 정렬된 채로 한 번에 가져와 앞의 것만 남긴다. 돌아오는 행이 "이 페이지 챌린지들의 보이는 인증 전부"라 인증이 쌓이면 무거워진다. 네이티브 윈도 함수로 바꾸면 챌린지당 한 행으로 줄지만 제외 조건이 QueryDSL 헬퍼에서 떨어져 나가 규칙이 갈라진다 — **인증 조회 인덱스 전략을 볼 때 같이 정한다.**

### `member_challenge`

회원의 챌린지 참여 상태다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 참여 회원, `member.id` FK |
| challenge_id | BIGINT | N | 대상 챌린지, `challenge.id` FK |
| participation_round | INT | N | 참여 회차. 재참여할 때마다 1 증가한다. 기본값 `1` |
| current_streak | INT | N | 연속 참여일, 기본값 `0` |
| last_verified_date | DATE | Y | 마지막 인증일. 참여 직후에는 값이 없다. |
| joined_at | DATETIME(6) | N | 참여 시각 |
| active | TINYINT(1) | N | 참여 중 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_id, challenge_id)`. 재참여는 새 행을 만들지 않고 기존 행의 `active`를 `true`로 되돌리며, `current_streak`을 0으로 초기화하고 **`participation_round`를 1 증가시킨다.**
- **`participation_round`는 지난 참여의 인증을 현재 참여와 분리하기 위한 것이다.** 회차가 없으면 이탈 전 기록이 현재 참여 상태에 섞인다.
- **회차가 하루 1회를 우회하는 수단이 되어서는 안 된다.** 도입 당시에는 "같은 날 이탈 후 재참여하면 다시 인증할 수 있다"가 의도였는데, 그 결과 나가기/들어오기를 반복해 하루에 인증 게시글을 얼마든지 올릴 수 있었다. 정책을 뒤집었다 — 아래 [주기 1회는 회차를 넘는다]를 볼 것.
- **연속 참여일은 현재 회차 기준이다.** 재참여하면 0에서 다시 시작한다("지금 며칠째 이어오는가"이므로).
- **인증 목록과 "오늘 완료 여부"는 회차를 보지 않는다.** 목록은 지난 참여의 기록도 보여주고(이탈이 기록을 지우지 않으므로), 오늘 완료 여부는 회차와 무관하게 그날 인증했는지로 판단한다.
- 참여 중인 챌린지 조회를 위해 `(member_id, active)` 인덱스가 필요하다.
- 이탈은 `active = false`로 표현한다. 참여 이력을 보존하므로 소프트 삭제(`deleted_at`)를 쓰지 않는다.
- **`active = false`는 "회원이 이 챌린지를 그만두었다"는 뜻이며, 회원 탈퇴와는 무관하다.** 회원이 탈퇴해도 이 테이블은 건드리지 않는다. 접근 차단은 `member.is_active` / `member.deleted_at`이 담당한다. 탈퇴를 여기에 기록하면 계정 복구 시 스스로 그만둔 챌린지와 탈퇴로 꺼진 챌린지를 구분할 수 없다.
- 참여자 수를 집계할 때는 `active = true`인 행만 세되, **탈퇴 회원의 참여가 섞이지 않도록 `member`를 조인해 활성 회원만 포함한다.**
- `current_streak`은 저장된 값을 그대로 신뢰하지 않는다. 조회 시 `last_verified_date`가 **이번 구간도 직전 구간도 아니면** 0으로 판정한다.

### `challenge_verification`

챌린지 참여의 일자별 인증 이력이다. 한 참여 회차 안에서 하루에 한 건만 존재한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_challenge_id | BIGINT | N | 대상 참여, `member_challenge.id` FK |
| participation_round | INT | N | 인증 당시의 참여 회차. `member_challenge.participation_round`의 스냅샷 |
| verified_date | DATE | N | 인증 기준일(KST) |
| period_start_date | DATE | N | 이 인증이 속한 주기 구간의 첫날. `DAILY` 면 `verified_date` 와 같고, `WEEKLY` 면 그 주 일요일, `MONTHLY` 면 그 달 1일. **유니크 키에 들어가 "주기 1회"를 DB 가 보장한다** |
| verified_at | DATETIME(6) | N | 인증 처리 시각 |
| image_url | VARCHAR(2048) | N | 인증 사진의 **S3 오브젝트 key**(전체 URL이 아니다). 사진 없는 인증은 허용하지 않는다. |
| content | VARCHAR(255) | Y | 인증 코멘트 |
| hidden_at | DATETIME(6) | Y | 신고 누적으로 가려진 시각. `NULL` 이면 노출된다. 아래 [신고 누적 자동 숨김] 참고 |
| review_status | VARCHAR(20) | N | AI 심사 결과. `APPROVED` · `PENDING`. 기본값 `APPROVED` |
| review_attempts | INT | N | 심사 재시도 횟수. 기본값 `0` |
| pending_since | DATETIME(6) | Y | 보류가 시작된 시각. `PENDING` 일 때만 값이 있다 |
| deleted_at | DATETIME(6) | Y | 본인이 지운 시각. `NULL` 이면 살아 있다 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_challenge_id, participation_round, period_start_date)`는 **한 회차 안에서의** 주기 1회를 DB가 보장한다. 같은 회차에서 동시 요청이 들어오면 하나만 통과한다.
  > **예전에는 키가 `verified_date` 였다.** 그래서 `WEEKLY` 챌린지에 월·화·수 매일 인증해도 DB가 막지 않았다. 애플리케이션에서 "이번 주에 있나"를 조회해 막아도 **동시 요청 두 건이 모두 통과**하는데, 이 프로젝트는 인증의 동시성을 유니크 제약에 맡기고 있어 대체할 것이 없었다.
  >
  > `DAILY` 에서는 두 컬럼 값이 같으므로 **새 키가 옛 키와 완전히 같다.** 기존 데이터·동작이 그대로 유지되고 백필은 `verified_date` 복사로 끝난다.
  >
  > ⚠️ **키를 바꾸는 순서가 중요하다.** 옛 유니크 키는 선두 컬럼이 `member_challenge_id` 라 `member_challenge` 로 향하는 **외래 키의 인덱스를 겸한다.** 먼저 지우면 MySQL 이 errno 1553 으로 거절한다 — 실제로 그 순서로 썼다가 마이그레이션이 컬럼만 추가된 채 죽었다. **새 키를 먼저 걸고 옛 키를 지운다.**
- **회차를 넘는 주기 1회는 이 제약이 막지 못한다.** 키에 `participation_round`가 들어 있어 재참여로 회차가 오르면 같은 구간도 통과하기 때문이다. 그 부분은 저장 경로의 비관 잠금 + 애플리케이션 검증으로 막는다(아래 [주기 1회는 회차를 넘는다]). 제약에서 회차를 빼지 않은 것은 정책 이전에 쌓인 중복 행이 남아 있어서다.
- 인증을 나열할 때는 회차로 좁히지 않는다. 지난 회차의 인증도 그 회원이 남긴 기록이며, 회차는 응답에 실어 "이번 참여 / 지난 참여"를 구분한다.
- 챌린지 단위 피드는 `member_challenge`를 경유한다. `member_challenge`를 `(challenge_id, active)` 인덱스로 좁힌 뒤 `challenge_verification`을 `member_challenge_id`로 조인한다. **이 조인에 별도 인덱스를 추가할 필요는 없다** — 위 유니크 제약의 선두 컬럼이 `member_challenge_id`라 그 인덱스가 조인과 "이번 구간 인증 행 찾기"를 모두 커버한다.

#### 주기를 바꿀 때는 백필이 함께 필요하다

`period_start_date`는 **저장 시점의 주기로 계산해 못 박은 값**이다. 챌린지의 `routine_cycle`을 바꿔도 옛 행은 옛 기준으로 남는다.

**백필 없이 바꾸면 그 구간이 뚫린다.**

```
DAILY 로 월요일에 인증  → period_start_date = 월요일
WEEKLY 로 바꾼 뒤 수요일에 다시 인증 → 이번 구간 첫날 = 그 주 일요일
→ 유니크 키에도 조회에도 안 걸린다 → 그 주에 한 건 더 들어간다
```

**스트릭도 함께 어긋난다.** `current_streak`은 `last_verified_date`를 주기 단위로 해석해 세므로(위 [연속 참여일] 참고), 일 단위로 쌓인 값을 주 단위로 읽게 된다 — "30일 연속"이 "30주 연속"이 된다.

**하필 그 경로가 조용하다.** `routine_cycle`은 `R__seed_challenge.sql`의 upsert로 바뀐다. 값을 한 글자 고치고 머지하면 다음 배포에 그냥 덮어써진다. 마이그레이션처럼 이력이 남지도, 리뷰에서 눈에 띄지도 않는다.

##### 단순 UPDATE로 끝나지 않는다

유니크 키는 `(member_challenge_id, participation_round, period_start_date)`이고 **`deleted_at`이 들어 있지 않다.** 즉 본인이 지운 인증도 키를 차지한다. 주기를 넓히면 여러 행이 **같은 구간으로 접히면서** 그 키에 걸린다.

```
DAILY  월·수 인증 (period_start_date 가 각각 다름)     → 통과
WEEKLY 로 다시 계산하면 둘 다 그 주 일요일이 된다      → 유니크 키 위반
```

그래서 **접히는 행이 있는지 먼저 확인하고, 남길 행만 갱신한다.**

##### 남기지 못한 행을 지우지는 않는다

`challenge_verification`은 **신고와 좋아요가 외래 키로 참조한다.**

```
challenge_verification_report.challenge_verification_id  → challenge_verification.id
challenge_verification_like.challenge_verification_id    → challenge_verification.id
```

물리 삭제는 그 제약에 걸린다. 그래서 접힌 행은 `deleted_at`을 채워 소프트 삭제한다.

**다만 소프트 삭제한 행의 `period_start_date`는 건드리지 않는다.** 유니크 키에 `deleted_at`이 없어 지운 행도 키를 차지하므로, 같이 새 구간으로 옮기면 **여전히 충돌한다.** 옛 값으로 두면 새 구간과 겹치지 않는다 — 그 자리가 이 절차의 핵심이다.

##### 절차

아래 `:periodExpr`는 새 주기의 구간 첫날 계산식이다. **충돌 확인과 백필이 반드시 같은 식을 써야 한다.**

| 새 주기 | `:periodExpr` |
| --- | --- |
| `DAILY` | `cv.verified_date` |
| `WEEKLY` | `DATE_SUB(cv.verified_date, INTERVAL DAYOFWEEK(cv.verified_date) - 1 DAY)` |
| `MONTHLY` | `DATE_FORMAT(cv.verified_date, '%Y-%m-01')` |

> `DAILY`로 **좁히는** 방향은 구간이 잘게 나뉘므로 접히는 행이 생기지 않는다. 그래도 1번은 돌려서 확인한다 — 전제를 확인 없이 믿지 않는다.

**0) 쓰기를 멈춘다.** 절차가 도는 동안 그 챌린지에 인증이 들어오면 방금 계산한 보존 대상이 어긋난다. 인증 저장은 참여 행을 비관 잠금으로 잡으므로, 대상 `member_challenge`를 같은 방식으로 잠근 채 1~4를 한 트랜잭션에서 끝내거나, 그게 어려우면 `challenge.active = FALSE`로 잠시 내려 새 인증을 막는다.

**1) 접히는 행 확인**

```sql
SELECT cv.member_challenge_id, cv.participation_round,
       :periodExpr AS new_period, COUNT(*) AS cnt
FROM challenge_verification cv
JOIN member_challenge mc ON mc.id = cv.member_challenge_id
WHERE mc.challenge_id = :challengeId
  AND cv.deleted_at IS NULL
GROUP BY 1, 2, 3
HAVING cnt > 1;
```

**2) 보존할 행을 id로 확정한다.** 구간별로 어느 인증을 남길지는 정책 판단이다. 아래는 **가장 이른 것을 남기는** 예다.

```sql
CREATE TEMPORARY TABLE keep_ids AS
SELECT MIN(cv.id) AS id
FROM challenge_verification cv
JOIN member_challenge mc ON mc.id = cv.member_challenge_id
WHERE mc.challenge_id = :challengeId
  AND cv.deleted_at IS NULL
GROUP BY cv.member_challenge_id, cv.participation_round, :periodExpr;
```

**3) 보존하지 않은 행을 소프트 삭제한다.** `period_start_date`는 그대로 둔다.

```sql
UPDATE challenge_verification cv
JOIN member_challenge mc ON mc.id = cv.member_challenge_id
SET cv.deleted_at = NOW(6)
WHERE mc.challenge_id = :challengeId
  AND cv.deleted_at IS NULL
  AND cv.id NOT IN (SELECT id FROM keep_ids);
```

**4) 보존 행만 백필한다.**

```sql
UPDATE challenge_verification cv
JOIN member_challenge mc ON mc.id = cv.member_challenge_id
SET cv.period_start_date = :periodExpr
WHERE mc.challenge_id = :challengeId
  AND cv.id IN (SELECT id FROM keep_ids);
```

**5) 스트릭을 함께 정리한다.** `current_streak`만 손대면 안 된다. `MemberChallenge.applyVerification()`이 **`last_verified_date`를 새 주기로 비교**하므로(`isSamePeriod` / `isPreviousPeriod`), 그 값을 그대로 두면 주기 변경 직후의 인증이 "이번 구간에 이미 인증됨"으로 판정되어 스트릭이 오르지 않거나 엉뚱하게 이어진다.

```sql
UPDATE member_challenge mc
SET mc.current_streak = 0,
    mc.last_verified_date = NULL
WHERE mc.challenge_id = :challengeId;
```

이력을 되짚어 정확히 다시 세려면 인증 행 전체를 순회해야 한다. **어디까지 복원할지는 그때 정한다** — 위는 가장 단순한 선택(초기화)이다.

**6) 쓰기를 재개하고 검증한다.** 1번 쿼리를 다시 돌려 결과가 비어 있는지, 그리고 새 주기로 이번 구간에 인증이 두 건 들어가지 않는지 확인한다.

> **이 절차는 아직 쓰인 적이 없다.** 지금까지 주기를 바꾼 챌린지가 없기 때문이지, 주기가 하나뿐이어서가 아니다 — 운영에는 `WEEKLY` 15개와 `MONTHLY` 13개가 이미 나가 있다. 그중 하나의 주기를 바꾸는 순간 위가 필요해진다.
- **피드의 정렬·커서 키는 `verified_at`이 아니라 `id`(내림차순)다.** 당일 재인증이 `verified_at`을 덮어쓰기 때문에, `verified_at`을 커서로 쓰면 페이지를 넘기는 도중 항목이 위로 점프해 중복·누락이 생긴다. `id`는 한 번 부여되면 변하지 않아 커서가 안정적이고, 하루 1건 제약상 오늘 인증은 어차피 상단에 온다. 따라서 `(member_challenge_id, verified_at)` 인덱스는 두지 않는다.
- **피드 정렬은 최신순(`id DESC`)과 좋아요순 둘이다.** 기본은 최신순 — 파라미터를 안 보내던 클라이언트의 화면이 조용히 달라지면 안 된다.
  - **좋아요순도 커서로 끝까지 넘긴다. 다만 커서 값이 둘이다** — `(좋아요 수, id)`. 좋아요 0개가 대부분이라 값이 겹쳐서, `id` 하나로는 "어디까지 봤는지"를 가릴 수 없다. 집계값으로 거르는 것이라 `where` 가 아니라 `having` 에 들어간다.
  - **근사 정렬이라는 것을 계약에 적는다.** 스크롤하는 동안 좋아요가 눌리면 그 인증의 순위가 바뀌고, 커서 경계를 넘나들면 **이미 본 것이 한 번 더 나오거나 한 건이 빠진다.** 정렬 키가 실시간으로 변하는 데이터라 어떤 페이징 방식으로도 없앨 수 없다.
  - **그래도 offset 보다 커서가 낫다.** offset 은 "몇 번째까지 봤나"라 앞에서 하나만 움직여도 그 뒤 전체가 밀리는 반면, 커서는 "무엇까지 봤나"라 경계 근처만 흔들린다. 이 저장소의 다른 목록이 전부 커서인 것과도 맞는다 — 정렬마다 페이징 규약이 갈리면 클라이언트가 두 벌을 다뤄야 한다.
  - **좋아요 수를 캐시 컬럼으로 두지 않는다는 결정은 그대로다.** 정렬을 위해 컬럼을 두려면 갱신·탈퇴 처리·재계산 배치가 따라오는데, 정작 그것으로 인덱스를 만들 수가 없다 — `challenge_verification` 에 `challenge_id` 가 없어 `(challenge_id, like_count, id)` 인덱스가 성립하지 않기 때문이다(그 비정규화가 아래 항목이다). 인덱스를 못 만들면 어차피 조인 후 정렬이라 캐시 컬럼의 이득이 남지 않는다.
  - **정렬용 집계와 표시용 집계가 갈리면 안 된다.** "탈퇴 회원의 좋아요는 뺀다"는 규칙이 `activeMember` 한 곳에 있고 양쪽이 그것을 쓴다. 갈리면 정렬은 5개 기준인데 화면에는 3개로 보인다.
- 참여자가 많아지면 조인 대상 `member_challenge` 행이 늘어 조인 후 정렬 비용이 커진다. 그 시점에는 `challenge_verification`에 `challenge_id`를 비정규화해 `(challenge_id, id)` 인덱스로 정렬까지 커버하는 방안을 검토한다. 현재 규모에서는 도입하지 않는다.
- **회원 단위로 집계하거나 나열하는 쿼리는 회차 중복을 반드시 제거한다.** "오늘 완료자 수", "오늘 인증한 사람 목록"처럼 **사람을 세거나 나열하는 것**은 `member_challenge_id` 기준으로 중복을 제거한다.
  - **현재 회차로 필터링하는 방식은 쓰지 않는다.** 그렇게 하면 지난 회차에 오늘 인증한 사람이 집계에서 빠지는데, 상세의 "현재 주기에 인증했는지"는 회차를 보지 않아 **버튼은 잠기는데 집계에는 없는** 상태가 된다. 두 값이 서로 다른 말을 하게 된다.
  - 하루 1회를 회차와 무관하게 막은 뒤로는 같은 날 회차가 다른 인증이 새로 생기지 않는다(아래 [주기 1회는 회차를 넘는다]). 그럼에도 중복 제거를 유지하는 것은 그 정책 이전에 쌓인 데이터가 남아 있기 때문이다.
- 반대로 **인증(사진·포스트) 단위로 나열하는 피드는 중복 제거를 하지 않는다.** 회차가 다른 두 인증은 실제로 별개의 인증 이벤트이므로 둘 다 보여주는 것이 맞다. **"무엇을 세고 있는가 — 사람인가, 인증인가"를 먼저 정하고 쿼리를 작성한다.**
> **심사 보류 컬럼 셋(`review_status` · `review_attempts` · `pending_since`)은 도입되어 표에 들어가 있다.** 각 값의 뜻과 도입 근거는 [심사가 장애로 답을 못 주면 보류한다](#심사가-장애로-답을-못-주면-보류한다)에 있다. 그 절은 결정 배경을 남긴 기록이지 미결 항목이 아니다.

- **이 테이블은 소프트 삭제한다(`deleted_at`).** 행을 지우지 않는 이유는 아래와 같다.
  - **유니크 제약이 지운 행도 그대로 본다.** MySQL 의 유니크 제약은 `deleted_at IS NULL` 을 모른다. 그래서 지운 뒤 같은 구간에 다시 인증하는 것은 INSERT 가 아니라 **지운 행을 되살리는 UPDATE** 로 처리한다. 저장 경로가 지운 행까지 찾아야 하는 이유가 이것이다(아래 [지운 행을 어느 조회가 보는가]).
  - **행이 남아야 `challenge_verification_report`·`challenge_verification_like` 의 외래 키가 깨지지 않는다.** 물리 삭제하면 신고·좋아요가 함께 사라져 신고 누적 이력이 없어진다.
  - **`hidden_at` 과 뜻이 다르다.** 그쪽은 신고가 쌓여 남이 가린 것이고, 이쪽은 본인이 내린 것이다. 둘을 한 컬럼으로 합치면 "가려진 글을 지워 신고 누적을 회피하는" 길이 열린다.
- ~~오늘 완료자 수~~ — **응답에서 뺐다(#114).** 상세 화면은 참여자 수·리워드·인증 게시글 수만 보여 주고 이 값을 그리지 않는다. 아무도 안 읽는 값을 위해 상세를 열 때마다 조인 3개짜리 `COUNT(DISTINCT)` 가 돌고 있었다.
  > 되살릴 일이 생기면 **주기를 먼저 정해야 한다.** `WEEKLY` 챌린지에서 "오늘 몇 명"은 버튼 기준(현재 주기)과 시간 단위가 어긋난다 — 그 어긋남을 풀지 않은 채로 두었던 것이 제거의 또 다른 이유다.

#### 주기 1회는 회차를 넘는다

**이번 구간에 인증한 뒤 이탈했다가 재참여해도 그 구간에는 더 인증할 수 없다.** 회차가 올라가도 인증한 사실은 남기 때문이다.

원래는 반대였다. 위 [회차를 두는 이유]에 적힌 대로 회차는 "같은 날 이탈 후 재참여"를 가능하게 하려고 도입한 것인데, 그 결과 **나가기/들어오기를 반복하면 하루에 인증 게시글을 얼마든지 올릴 수 있었다.** 실제로 운영에서 두 건이 그렇게 생겼다. 그래서 정책을 뒤집었다.

두 곳이 같은 기준을 쓴다.

| | 기준 |
| --- | --- |
| 인증 저장 | 회차를 빼고 **`period_start_date` 로** 이번 구간 인증을 찾는다. 지난 회차 것이면 409 |
| 상세의 인증 여부 | 인증 테이블을 직접 본다. **저장 경로와 같은 컬럼(`period_start_date`)을 본다** — 구간 경계 계산이 두 벌이면 버튼과 실제 동작이 어긋난다. 참여 행의 `last_verified_date`는 재참여가 초기화하므로 쓰지 않는다 |

**유니크 제약은 `(member_challenge_id, participation_round, period_start_date)`다.** 같은 회차 안의 주기 1회는 이 제약이 보장한다. 다만 **회차가 들어 있어 회차를 넘는 부분은 DB가 막지 못한다** — 그 부분은 저장 경로가 참여 행을 비관 잠금으로 잡은 뒤 확인한다. 제약에서 회차를 빼려면 정책 이전에 쌓인 중복 행을 먼저 정리해야 하는데, 그 데이터는 남기기로 했다.

> **애플리케이션 선검사를 지우면 안 된다.** 위 문장의 요점은 제약이 회차를 넘지 못한다는 것이다. 제약이 생겼다고 선검사를 걷어내면 나가기/들어오기로 같은 구간에 두 번 인증할 수 있게 된다.
- 참여자 수와 마찬가지로, 집계 시 `member`를 조인해 **탈퇴 회원의 인증이 섞이지 않도록 한다.**

### `challenge_verification_report`

인증 사진에 대한 신고다. 신고자 본인의 피드에서 즉시 가리고, 임계값만큼 쌓이면 전체 회원에게 가린다(아래 [신고 누적 자동 숨김]).

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| challenge_verification_id | BIGINT | N | 신고 대상 인증, `challenge_verification.id` FK |
| reporter_id | BIGINT | N | 신고한 회원, `member.id` FK |
| report_type | VARCHAR(20) | Y | 신고 사유 종류. `IRRELEVANT`·`REUSED`·`STOLEN`·`SPAM`·`ETC`. **NULL 은 사유 선택 도입 전에 들어온 신고** |
| reason | VARCHAR(255) | Y | 직접 입력한 사유. `ETC` 일 때만 채워진다 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(challenge_verification_id, reporter_id)`로 중복 신고를 막는다. **이 제약이 피드 필터의 인덱스이기도 하다** — 피드 쿼리가 `NOT EXISTS (... where challenge_verification_id = ? and reporter_id = ?)` 형태라 두 컬럼을 그대로 탄다. 그래서 신고용 인덱스를 따로 두지 않는다.
- 중복 신고는 "이미 신고했는지"를 먼저 조회해 판단하지 않는다. 조회와 저장 사이에 같은 요청이 두 번 들어오면 둘 다 통과하기 때문이다. 유니크 제약 위반을 잡아 409로 바꾼다(인증 저장과 같은 방식).
- 신고는 인증을 삭제하지 않는다. 신고 즉시 신고자 본인에게만 제외되고, 임계값만큼 쌓이면 전체 회원에게 가려진다.
- 자기 인증을 신고하는 것을 막지 않는다. 기획에 그런 제약이 없다. 다만 자기 신고도 임계값 집계에 포함되므로 "본인 화면에서만 안 보인다"로 끝나지 않는다 — 다른 회원의 신고와 합쳐져 전체 숨김에 기여한다. 한 사람이 한 번만 신고할 수 있어 혼자서는 임계값을 채울 수 없다.
- 신고 취소는 제공하지 않는다. 기획에 없다.
- 신고가 임계값만큼 쌓이면 **전체 회원에게 가린다.** `challenge_verification.hidden_at` 에 시각을 기록하고, 인증을 읽는 모든 경로가 그 값이 `NULL` 인 것만 본다. 자세한 내용은 아래 [신고 누적 자동 숨김] 을 볼 것.

#### 신고 사유

**사유 선택은 필수다.** 예전에는 자유 입력 `reason` 하나뿐이라 신고를 모아 봐도 무엇 때문인지 알 수 없었다 — 대부분 비어 있었고, 채워져 있어도 자유 문장이라 셀 수가 없었다.

| `report_type` | 뜻 | `reason` |
| --- | --- | --- |
| `IRRELEVANT` | 실제 루틴 수행과 무관한 사진 | 받지 않는다 |
| `REUSED` | 예전 인증 사진의 재사용 | 받지 않는다 |
| `STOLEN` | 타인 사진 도용 | 받지 않는다 |
| `SPAM` | 스팸·광고성 | 받지 않는다 |
| `ETC` | 그 밖 | **필수**, 1~100자 |

**화면 문구는 서버가 갖지 않는다.** 클라이언트가 이 값에 맞는 문장을 가진다 — 서버가 내려주면 문구를 고칠 때마다 배포가 필요하다. 에러 메시지를 코드로만 만드는 것과 같은 기준이다.

**컬럼은 `NULL` 을 허용하되 요청 DTO 가 필수로 막는다.** `NOT NULL` 로 두려면 기존 행을 채워야 하는데, `ETC` 로 채우면 "사용자가 기타를 고른 것" 과 "그때는 사유가 없던 것" 이 통계에서 섞인다. `NULL` 인 행은 "그때는 없던 값" 이라는 사실 그대로다.

**`ETC` 가 아닌데 `reason` 이 함께 오면 거절하지 않고 버린다.** 라디오를 바꿀 때 입력란 값을 지우지 않고 보내는 실수 때문에 신고 자체가 실패하는 편이 더 나쁘다. 다만 저장하지도 않는다 — 화면에 없던 값이 남으면 통계가 어긋난다.

**`reason` 은 컬럼이 255자지만 요청 검증은 100자다.** 화면이 100자라, 그보다 긴 값을 받아 주면 다른 경로로 들어온 글이 화면에서 잘려 보인다. 컬럼을 줄이는 마이그레이션은 얻는 것에 비해 위험해 그대로 둔다.

> **사유는 숨김 판정에 쓰이지 않는다.** 가려지는 기준은 그대로 신고 **건수**다. 사유별 가중치를 두면 "몇 건이면 가려지나"가 사유 조합에 따라 달라져 설명하기 어렵고, 오탐일 때 되짚기도 어렵다. **우선 기록만 하고** 데이터가 쌓인 뒤 판단한다.

#### 신고 누적 자동 숨김

**데모 전까지의 임시 조치다.** 장기적으로는 AI 심사와 관리자 UI 가 판단·복구를 다룬다.

**세는 시점은 조회가 아니라 신고 때다.** 신고를 INSERT 한 뒤 그 인증의 신고 수를 세고, 임계값 이상이면 `hidden_at` 을 기록한다. 피드에서 매번 `having count(*) >= N` 으로 세면 읽기 경로가 무거워지는데, 신고는 드물고 조회는 잦으므로 쓰기 시점 계산이 맞다.

두 신고가 동시에 임계값을 넘겨 `hidden_at` 을 두 번 써도 결과가 같다(엔티티가 이미 가려졌으면 덮어쓰지 않는다). 동시 신고로 숨김이 누락되는 문제는 아래 [임계값] 절에 적은 잠금·격리 수준으로 막는다.

**`hidden_at` 은 소프트 삭제가 아니다.** 인증 행도 스트릭도 그대로 두고 **노출만** 막는다. 그래서 다음 두 가지는 숨김의 영향을 받지 않는다.

| | 숨김 반영 | 왜 |
| --- | --- | --- |
| 인증 피드 · 내 인증 목록 · 인증 게시글 수 | **제외한다** | 노출 경로다 |
| 스트릭 | **그대로 센다** | 수행 기록이지 노출이 아니다. 신고당했다고 달성이 취소되지는 않는다 |

**작성자 본인에게도 가린다.** 본인만 보이게 하면 `/me` 만 예외가 되어 응답에 상태 필드가 붙고 조회마다 분기가 생긴다. 임시 조치의 범위를 넘는다고 보아 조건 하나로 통일했다. 대신 작성자는 아무 설명 없이 자기 사진이 사라진 것을 보게 된다 — 인지하고 감수한 선택이다.

**임계값은 설정값이다**(`challenge.report.hide-threshold`, 기본 3). 실사용자 수에 따라 적정값이 달라지고, 오탐이 번질 때 값을 올려 그 뒤로 덜 가려지게 할 수 있다.

> ⚠️ **임계값을 올려도 이미 가려진 것은 되살아나지 않는다.** 조회는 `hidden_at IS NULL` 만 보는데 그 값을 해제하는 경로가 없다. 값을 올리면 **새로 가려지는 것만** 줄어든다.
>
> **되살리려면 그 행의 `hidden_at` 을 DB 에서 직접 `NULL` 로 되돌려야 한다.** 관리자 API 는 이 임시 조치의 범위를 넘는다고 보아 넣지 않았다. 실사용자를 본격적으로 받는다면 그때 복구 경로를 갖춰야 한다.

**동시 신고에서도 누락되지 않는다.** 신고 저장 전에 인증 행을 비관 잠금으로 잡고, 트랜잭션 격리를 `READ_COMMITTED` 로 낮춘다. **둘 다 필요하다** — 잠금만으로는 순서만 정해지고, REPEATABLE READ 에서는 트랜잭션 첫 조회에 스냅샷이 고정되어 잠금을 잡고 기다린 뒤에 세어도 그동안 커밋된 신고가 보이지 않는다. 잠금 없이 세면 정확히 임계값만큼만 동시에 들어왔을 때 그 뒤로 신고가 없는 한 영원히 가려지지 않는다.

**여러 계정이 담합하면 임의의 게시물을 내릴 수 있다.** 소셜 로그인이라 계정 생성 장벽도 낮다. 데모 범위에서는 인지하고 감수한다. 방어를 넣으면 임시 조치가 임시가 아니게 된다.

### `challenge_verification_like`

인증 게시물에 대한 좋아요다. 챌린지가 아니라 **인증 한 건**에 붙는다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| challenge_verification_id | BIGINT | N | 좋아요 대상 인증, `challenge_verification.id` FK |
| member_id | BIGINT | N | 누른 회원, `member.id` FK |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- **취소는 행 삭제(하드 삭제)다.** 소프트 삭제(`deleted_at`)를 쓰면 취소한 뒤 다시 누를 때 남아 있는 행이 유니크 제약에 걸린다. MySQL 유니크 제약은 `deleted_at IS NULL`을 모른다. `challenge_verification`에 `deleted_at`을 두지 않은 것과 같은 논거이며, 좋아요는 신고와 달리 **취소가 일상적으로 반복되는 동작**이라 이 문제가 바로 드러난다. 이력 보존 가치도 낮다.
- `UNIQUE(challenge_verification_id, member_id)`로 중복을 막는다. **선두 컬럼이 `challenge_verification_id`라 피드 페이지의 인증 id 목록으로 좋아요를 배치 집계하는 쿼리가 이 인덱스를 그대로 탄다.** 그래서 별도 인덱스를 두지 않는다.
- "내가 좋아요한 목록"을 보여주는 화면이 생기면 그때 `(member_id, ...)` 인덱스를 검토한다. 지금은 그 조회가 없다.
- **좋아요 수를 캐시 컬럼으로 두지 않는다.** 참여자 수·인증 게시글 수와 같은 원칙이다(위 `challenge` 절). 좋아요는 그 둘보다 훨씬 자주 바뀌어 캐시의 정합성 비용이 더 크다. 대신 피드는 페이지의 인증 id 목록으로 `GROUP BY` 한 번에 묶어 집계한다 — 건별 집계는 N+1이 된다.
- **집계 시 탈퇴 회원의 좋아요는 제외한다.** 참여자 수·인증 게시글 수 집계와 같은 규칙이다.
- **좋아요·취소는 멱등하다.** 이미 누른 상태에서 다시 누르거나 누르지 않은 상태에서 취소해도 성공으로 처리하고 최종 상태를 응답에 싣는다. 좋아요는 신고와 달리 토글이라 반복 호출이 정상 사용이고, 따닥 눌렀을 때 오류를 돌려주면 화면이 흔들린다. (신고는 한 번뿐인 행위라 중복을 409로 막는다 — 성격이 다르다.)
- **자기 인증에 좋아요를 허용한다.** 막으면 검증 분기와 에러 코드가 늘지만 얻는 것이 적다.
- **당일 재인증 시 좋아요가 승계된다.** 재인증은 행을 지우고 새로 만드는 것이 아니라 `image_url`·`content`·`verified_at`을 덮어쓰므로, 좋아요 행의 FK가 그대로 유지된다. 사진이 바뀌어도 좋아요 수가 남는다는 뜻이다. 비우려면 재인증 때 좋아요를 지우는 처리가 붙어야 하는데, 재인증은 대체로 사진 교체라 그대로 두는 편이 자연스럽다.
- 신고로 가려진 인증(#15)이나 임계값 숨김(#60)에 좋아요를 막는 처리는 **아직 없다.** 피드에서 안 보여도 `verificationId`를 알면 API를 직접 부를 수 있다. **#60을 구현할 때 이 지점을 함께 본다.**

## 재화(지갑) 테이블

회원이 가진 재화의 **잔액**과 그 잔액이 왜 그 값이 되었는지의 **거래 원장**이다. 상점의 구매·충전과 챌린지 리워드가 전부 이 위에서 돈다.

### 재화는 두 종류다

| 코드값 | 시안 표기 | 성격 | 얻는 법 |
| --- | --- | --- | --- |
| `GEM` | 파란 보석 (충전 팝업에서는 "코인") | **유료 재화** | 현금 결제 · 충전 보너스 |
| `TOPAZ` | 주황 보석 ("토파즈") | **무료 재화** | 챌린지 인증 리워드 |

**둘은 서로를 대신하지 못한다.** `GEM` 으로만 살 수 있는 아이템이 있고 `TOPAZ` 로만 살 수 있는 아이템이 따로 있다. 한쪽이 모자란다고 다른 쪽으로 결제되지 않는다.

**`GEM` 은 챌린지로 벌 수 없다.** 리워드를 `GEM` 으로 주면 인증만 하면 유료 재화가 공짜로 생긴다. 그래서 `challenge.reward` 는 `TOPAZ` 로 나간다.

**서버는 코드값만 갖고 한글 명칭은 클라이언트가 붙인다.** 시안 안에서도 표기가 갈린다 — 탭은 "파란보석"인데 충전 팝업은 "550코인"이다. 문구를 서버가 내려주면 문안을 고칠 때마다 배포가 필요해진다. 스트릭 문구·신고 사유 문구를 클라이언트가 갖는 것과 같은 기준이다.

> ⚠️ **이 표는 두 번 뒤집힌 적이 있다.** 지갑을 만들 때는 어느 쪽이 무엇인지 정해지지 않아 두 재화를 대칭으로 두었고, 그 사이 **추정으로 채운 값**이 사실처럼 남았다. 그 값을 근거로 리워드를 `GEM` 으로 지급하는 코드가 들어갔다가 되돌렸다. **재화 작업 전에는 이 표를 기획에 한 번 확인할 것** — 표에 값이 있다고 그것이 확정이라는 뜻은 아니었다.

#### 무료·유료는 재화 종류이고, 유상·무상은 그 안의 회계다

두 구분이 이름이 비슷해 섞이기 쉬우므로 갈라 둔다.

| 구분 | 무엇을 가르나 | 어디에 있나 |
| --- | --- | --- |
| **무료 재화 / 유료 재화** | **무엇을 살 수 있나.** 아이템마다 결제 재화가 정해져 있다 | `currency` 컬럼(`GEM`/`TOPAZ`) |
| **무상 / 유상 잔액** | **그 잔액이 현금에서 왔나.** 환불·소멸 정책을 가른다 | `free_balance`/`paid_balance` |

**`TOPAZ` 는 전부 무상이다.** 현금으로 사지 않으므로 `TOPAZ.paid_balance` 는 쓰지 않는다(항상 `0`).

**유상·무상이 실제로 갈리는 곳은 `GEM` 뿐이다** — 현금으로 산 몫(`paid`)과 충전 보너스(`free`)다. 충전 팝업이 `550코인` 과 `+50 보너스` 를 화면에서 이미 구분하고 있는 그 둘이다.

#### 무료 재화에 유상 잔액을 만들 수 없다 — 코드가 막는다

리워드 회수는 무료 재화(`TOPAZ`)에서 일어난다. 그 재화에 유상 잔액이 생기면 **회수가 환불 대상 재화를 깎기 시작한다** — 무상분을 다 쓴 사용자가 글을 지우면 현금으로 산 몫에서 빠진다.

**그 규칙을 `Currency` 가 갖는다.**

```java
GEM(true)      // 유상 잔액이 생길 수 있다 — 현금으로 산다
TOPAZ(false)   // 무료 재화라 유상 잔액이 있을 수 없다
```

지급 경로 둘이 이 값을 본다. `WalletCommandService.grant` 가 **지갑을 만들기 전에** 거절하고(`WALLET400_2`), `MemberWallet.grant` 도 스스로 막는다 — 서비스를 거치지 않고 엔티티를 직접 다뤄도 뚫리지 않는다.

> **왜 DB 제약이나 지갑 코드가 아니라 `Currency` 에 두었나.** "무료냐 유료냐" 는 **재화의 성질**이다. `CHECK (currency <> 'TOPAZ' OR paid_balance = 0)` 처럼 재화 이름을 스키마에 박으면, 성질이 바뀔 때 고쳐야 할 자리가 흩어진다. **이 값은 실제로 세 번 뒤집혔다** — 위 [재화는 두 종류다] 의 경고를 볼 것.

**지금 이 값을 만드는 코드는 없다.** 막아 두는 이유는 **교환(결제 이슈)을 만들며 "결제한 것이니 유상으로 기록하자" 는 판단이 나올 수 있기 때문**이다. 그 판단이 코드로 들어오는 시점에 걸린다.

### `member_wallet`

회원별·재화별 잔액이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 소유 회원, `member.id` FK |
| currency | VARCHAR(20) | N | 재화 종류. `GEM` · `TOPAZ` |
| paid_balance | INT | N | **유상** 잔액(현금으로 산 것). 기본값 `0` |
| free_balance | INT | N | **무상** 잔액(보너스·리워드·이벤트). 기본값 `0` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_id, currency)` — 한 회원의 한 재화에 지갑은 하나다. **동시에 두 요청이 지갑을 만들려 하면 하나만 통과한다.**
- **행은 필요할 때 만든다.** 가입 시점에 미리 만들지 않는다. 재화 종류가 늘 때마다 기존 회원 전부에게 백필해야 하고, 가입 흐름(다른 도메인)을 건드려야 하기 때문이다. 잔액을 조회하는 쪽은 **행이 없으면 0** 으로 읽는다.
- **재화 종류를 컬럼이 아니라 행으로 둔다.** `gem_balance`·`topaz_balance` 처럼 컬럼으로 두면 재화가 하나 늘 때마다 `ALTER TABLE` 이 필요하다.

#### 유상과 무상을 왜 나누는가

충전 팝업이 `550코인` 과 `+50 보너스` 를 **화면에서 이미 구분**하고 있다. 합쳐서 저장하면 **나중에 못 쪼갠다.**

- **환불** — 전자상거래법상 미사용 유상 재화는 청약철회 대상이다. 한 덩어리면 "환불 가능한 금액"을 계산할 수 없다
- **소멸** — 무상에만 유효기간을 두는 정책이 흔하다
- **소급이 불가능하다** — 나중에 나누려면 과거 거래가 어느 쪽이었는지 판정해야 하는데, 그 정보가 애초에 기록되지 않는다

#### 차감은 무상부터 한다

**`free_balance` 를 먼저 쓰고, 모자라면 `paid_balance` 에서 뺀다.**

- 유상 잔액을 남겨 두어야 환불 요구에 그대로 응할 수 있다
- 무상에는 소멸 정책이 붙을 수 있으므로 먼저 쓰는 것이 사용자에게 유리하다

**순서를 반대로 하면 환불 가능액이 줄어든다** — 같은 소비를 해도 사용자가 손해를 본다.

이 순서는 **한 재화 안에서만** 적용된다. 재화를 넘나드는 차감은 없다 — `GEM` 이 모자라다고 `TOPAZ` 가 빠지지 않는다.

> **`TOPAZ` 에서는 이 순서가 눈에 띄지 않는다.** 유상 잔액이 없으므로 항상 무상에서만 빠진다. 실제로 갈리는 것은 `GEM` 이다.

### `wallet_transaction`

잔액이 움직인 모든 기록이다. **이 테이블이 진실이고 `member_wallet` 은 그것을 더한 결과다.**

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 대상 회원, `member.id` FK |
| currency | VARCHAR(20) | N | 재화 종류 |
| transaction_type | VARCHAR(30) | N | 거래 종류. 아래 참고 |
| paid_delta | INT | N | 유상 잔액 증감. 차감이면 음수 |
| free_delta | INT | N | 무상 잔액 증감. 차감이면 음수 |
| paid_balance_after | INT | N | 거래 직후 유상 잔액 |
| free_balance_after | INT | N | 거래 직후 무상 잔액 |
| idempotency_key | VARCHAR(150) | N | **멱등 키.** `(member_id, currency, idempotency_key)` 유니크 |
| reference_type | VARCHAR(30) | Y | 근거가 된 대상의 종류(예: 인증·아이템·충전) |
| reference_id | BIGINT | Y | 그 대상의 id |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_id, currency, idempotency_key)` — **같은 요청이 두 번 들어와도 두 번 반영되지 않는다.** 네트워크가 끊겨 클라이언트가 재시도하는 것은 정상 동작이고, 그때 두 번 차감되거나 두 번 지급되면 그대로 돈 문제가 된다.
  - 키는 **호출부가 뜻이 담기게 만든다** — 예: 인증 하나에 대한 리워드는 그 인증을 가리키는 키 하나뿐이라, 같은 인증으로 두 번 지급하는 길이 아예 없다.
  - ⚠️ **회원을 유니크에 넣지 않으면 조용히 돈이 샌다.** 호출부가 자연스럽게 만드는 키는 회원을 담지 않는다(`"아이템 7 구매"`). 전역 유니크면 **다른 회원이 같은 아이템을 살 때 이미 처리된 요청으로 취급되어 차감이 건너뛰어진다** — 호출부는 성공으로 보고 물건을 내주므로 에러가 나지 않는다. 실제로 그렇게 만들었다가 테스트로 잡았다.
  - ⚠️ **재화도 넣어야 한다.** 교환은 한 재화를 빼고 다른 재화를 넣는 **한 사건**이라 호출부가 같은 키를 쓰는 것이 자연스럽다. 재화가 빠져 있으면 **들어오는 쪽이 이미 처리된 요청이 되어 사용자는 낸 것만 잃고 받지 못한다.**
  - **같은 회원·재화에 같은 키가 다른 종류의 거래로 들어오면 실패시킨다.** 되돌려주면 차감이 안 됐는데 성공으로 보인다 — 조용히 돈이 새는 쪽이라 시끄럽게 실패하는 편이 낫다.
  - **대신 회원을 넘나드는 중복은 이 제약이 막지 못한다.** 같은 결제 영수증이 두 계정에 쓰이는 것은 **영수증 자체에 유니크를 거는 쪽(결제 기록)이 막아야 한다.** 충전을 만들 때 반드시 함께 넣는다.
  - **나중에 붙일 수 없다.** 멱등 키 없이 만든 API 로 오간 거래는 소급해서 보호되지 않는다.
- 회원별 거래 내역 조회는 위 유니크 키가 선두 컬럼(`member_id`)으로 덮는다. 같은 선두 컬럼의 인덱스를 하나 더 두면 쓰기 비용만 늘고 얻는 것이 없다.
- **행을 수정하거나 지우지 않는다.** 잘못된 거래는 반대 방향 거래를 새로 쌓아 상쇄한다(회계 원장과 같다). 고쳐 쓰면 "그때 무슨 일이 있었나"가 사라진다.
- `paid_balance_after` · `free_balance_after` 를 남기는 이유는 **잔액이 틀어졌을 때 어느 거래에서 어긋났는지 짚기 위해서**다. 원장을 처음부터 더한 값과 이 값이 다르면 그 지점이 사고 지점이다.

#### 거래 종류

| 값 | 뜻 |
| --- | --- |
| `CHALLENGE_REWARD` | 챌린지 인증 통과 리워드 지급 |
| `CHALLENGE_REWARD_CLAWBACK` | 인증을 지워 리워드 회수 |
| `PURCHASE` | 아바타 아이템 구매 차감 |
| `TOPUP` | 현금 결제로 충전 |
| `EXCHANGE_OUT` / `EXCHANGE_IN` | 재화 간 교환(나간 쪽 / 들어온 쪽) |
| `REFUND` | 결제 취소로 회수 |
| `ADMIN_ADJUST` | 운영 보정 |

교환은 **두 행**이 된다. 재화가 다르면 한 행에 담을 수 없다.

#### 잔액을 왜 따로 들고 있나 — 원장만으로 계산하지 않는 이유

거래가 쌓일수록 매번 전부 더하는 비용이 커진다. 상점 헤더는 **모든 화면에서 보이는 값**이라 조회가 잦다. 그래서 `member_wallet` 을 **원장의 합계 캐시**로 둔다.

**둘은 반드시 같은 트랜잭션에서 갱신한다.** 한쪽만 쓰이면 그 순간부터 두 숫자가 서로 다른 말을 한다.

#### 동시 차감은 비관 잠금으로 막는다

잔액은 **유니크 제약으로 막을 수 없다.** 이 저장소는 "애플리케이션에서 검사하고 저장" 사이에 동시 요청 두 건이 모두 통과하는 것을 이미 겪었다(챌린지 재참여·인증).

지갑 행을 `SELECT ... FOR UPDATE` 로 잠근 뒤 검사하고 갱신한다. 같은 방식이 그룹 루틴·인증 신고에서 이미 쓰이고 있다.

> **잔액이 음수가 되는 경로가 없다는 것을 테스트로 증명한다.** 동시성은 코드를 읽어서 확신할 수 있는 종류의 문제가 아니다.

**그 보증을 DB 에도 건다** — `CHECK (paid_balance >= 0 AND free_balance >= 0)`. 잠금과 검사로 이미 막고 있지만, 나중에 잔액을 건드리는 경로가 하나 더 생기면 그 보증이 코드 리뷰에만 기대게 된다. MySQL 8.0.16 이상은 `CHECK` 를 실제로 강제한다(운영은 8.4).

> **잠금 대기 시간에는 상한을 두지 않았다.** MySQL 은 `FOR UPDATE` 에 `NOWAIT` · `SKIP LOCKED` 만 지원하고 **임의의 대기 시간을 지원하지 않는다** — JPA 의 `jakarta.persistence.lock.timeout` 힌트를 줘도 MySQL 다이얼렉트가 조용히 버린다(있는 것처럼 보이는 설정이 더 나쁘다). 실제 상한은 `innodb_lock_wait_timeout`(기본 50초)인데, 이 잠금은 한 행을 잡고 외부 호출 없이 곧바로 끝나므로 그 시간까지 가려면 비정상적인 경합이어야 한다. 상한이 필요해지면 애플리케이션이 아니라 세션 변수로 다룬다.

### `reward_grant`

**인증 한 건에 지금 유효한 지급이 있는가** 를 나타내는 상태다. 지급 이력 자체는 `wallet_transaction` 이 갖는다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 받은 회원, `member.id` FK |
| reason | VARCHAR(30) | N | 지급 사유. `VERIFICATION` · `STREAK_MILESTONE`(예약) |
| reference_id | BIGINT | N | 사유의 대상. `VERIFICATION` 이면 인증 id |
| currency | VARCHAR(20) | N | 지급한 재화 |
| amount | INT | N | 지급 수량 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_id, reason, reference_id)` — **중복 지급의 최종 방어선이다.** 따닥으로 두 번 들어와도 하나만 통과한다. 인증의 주기 1회를 유니크 키로 막은 것과 같은 방식이다.
- **당일 재인증은 행을 새로 만들지 않고 기존 행을 덮어쓰므로** `reference_id` 가 같다. 사진만 갈아끼우고 리워드를 또 받는 길이 이 제약으로 막힌다.

#### 이 테이블과 원장은 역할이 다르다 — 중복이 아니다

| | `reward_grant` | `wallet_transaction` |
| --- | --- | --- |
| 뜻 | **지금 유효한 지급** | **무슨 일이 있었나** |
| 회수하면 | **행을 지운다** | 반대 방향 거래를 쌓는다 |
| 목적 | 중복 지급 차단 | 이력·분쟁 대응 |

`member_wallet`(잔액)과 `wallet_transaction`(원장)을 나눈 것과 같은 구조다.

> **합치면 재지급이 막힌다.** 원장은 행을 지우지 않는 것이 원칙인데, 회수 뒤 다시 인증하면 지급이 되살아나야 한다. 원장 하나로 두면 그 인증에 대한 멱등 키가 이미 존재해 재지급이 조용히 건너뛰어진다 — **사용자는 지웠다가 다시 올린 뒤로 리워드를 영영 못 받는다.**

#### 멱등 키는 지급 행의 id 로 만든다

지갑에 넘기는 키는 `reward:grant:{reward_grant.id}` 다. 인증 id 로 만들면 회수 뒤 재지급이 같은 키가 되어 막히는데, **지급 행은 회수 때 지워지고 재지급 때 새 id 로 생기므로 키도 자연히 달라진다.**

#### 삭제는 회수하고, 모자라면 거부한다

```text
지급 기록 없음               →  삭제. 되돌릴 것이 없다
잔액 100, 그 인증의 지급 10  →  삭제 → 잔액 90
잔액   5, 그 인증의 지급 10  →  삭제 거부(409). 5 가 더 필요하다고 알린다
잔액   0, 그 인증의 지급 10  →  삭제 거부(409)
```

**전부 되돌릴 수 있어야 지울 수 있다.** 부분 회수는 없다 — 아래 두 줄은 결국 같은 조건(`잔액 < 지급액`)이다.

**부채를 만들지 않기 위해서다.** 미상환을 남기면 그만큼 제재를 걸어야 하는데, 제재 중에는 인증을 못 해 갚을 수단이 없다 — 빠져나올 수 없는 상태가 된다.

**지급 기록이 없으면 그냥 지워진다.** 리워드가 `0` 인 챌린지, 아직 보류라 지급되지 않은 인증, 이 기능이 생기기 전에 올린 인증이 그렇다. 받은 것이 없으니 되돌릴 것도 없다.

여기서 말하는 "잔액"은 **그 재화의 잔액**이고, 리워드는 `TOPAZ` 이므로 `TOPAZ` 잔액이다. `TOPAZ` 에는 유상 잔액이 없으므로 이 값은 곧 무상 잔액이다 — 위 [무료·유료는 재화 종류이고…] 참고.

**회수와 삭제는 한 트랜잭션이다.** 나뉘면 둘 중 하나만 일어나는 순간이 생긴다 — 재화만 빼앗기고 글은 남거나, 글은 지워졌는데 재화는 그대로다.

> **"올려서 이득만 챙기고 지우기" 를 막는 것은 이 회수뿐이다.** 인증 삭제는 스트릭을 건드리지 않기로 했다(시간 기준으로 깎으면 그만큼 기다려 우회되고, 소급하면 정당하게 지운 사람의 기록이 무너진다).

#### 보류였던 인증도 승인되면 지급한다

심사가 답을 못 해 보류된 인증은 저장은 되지만 지급하지 않는다. **나중에 재심사가 승인하면 그 시점에 지급한다.** 안 그러면 남의 서비스 장애로 보류된 사람만 리워드를 못 받는다 — 스트릭을 그때도 올려 주기로 한 것과 같은 기준이다.

## 아바타 아이템(상점) 테이블 — 설계안 (합의 필요)

> **아직 구현되지 않았다.** 이 절은 합의를 받기 위한 설계안이다. 슬롯 구성·구매 흐름·기본
> 제공 여부는 기획 확인을 받아 아래에 반영했다.

캐릭터에 입힐 아이템을 고르고, 사고, 착용해 저장한다. **재화 지갑 위에서 돈다** — 아이템마다 결제 재화(`GEM`/`TOPAZ`)가 정해져 있고, 둘은 서로를 대신하지 못한다.

### 기존 `product`·`member_order` 를 쓰지 않는다

문서에 이미 상품·주문 표가 있지만 **실물 배송 쇼핑**이다.

```text
member_order   receiver_name · receiver_phone · shipping_address
product        consumable_category_id FK  (소모품 분류)
```

아바타 아이템에는 배송지도 소모품 분류도 없고, 결제 수단이 현금이 아니라 **재화**다. 재사용하면 두 성격이 한 표에 섞여 어느 컬럼이 언제 의미 있는지 매번 따져야 한다. **별도 테이블로 간다.** 기존 표는 그대로 두되 위 [소모품 및 상품 테이블] 이 실물용이라는 것을 여기 적어 둔다.

### `avatar_item`

판매하는 아이템의 마스터 데이터다. **운영이 채우고 앱은 읽기만 한다.**

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키. **시드가 고정 id 로 넣는다** |
| slot | VARCHAR(30) | N | 점유 슬롯. 아래 [슬롯] 참고 |
| currency | VARCHAR(20) | N | 결제 재화. `GEM` · `TOPAZ` |
| price | INT | N | 가격. **`CHECK (price > 0)`** |
| name | VARCHAR(100) | N | 아이템 이름 |
| image_url | VARCHAR(2048) | N | 아이템 이미지. **현재 절대 URL(`https://placehold.co/...`)이며, 캐릭터 도메인 작업에서 `image_key`(S3 key)로 바꾼다** — 아래 [캐릭터 도메인] 절 참고 |
| sort_order | INT | N | 목록 정렬 순서. 기본값 `0` |
| active | TINYINT(1) | N | 판매 여부. 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- **`price` 는 `0` 일 수 없다.** 기본 제공 아이템을 두지 않기로 했으므로(아래 [기본 제공 아이템은 없다]) `0` 짜리 아이템이 존재할 이유가 없다. 값이 없는 채로 두면 **무료 아이템을 구매로 볼지, 자동 보유로 볼지, 그냥 입히는지가 구현마다 갈린다.** 제약으로 막는다.
- **`UNIQUE(id, slot)` 을 함께 건다.** 기본 키가 있으니 중복 방지에는 필요 없지만, `member_avatar_equipment` 가 `(avatar_item_id, slot)` 복합 FK 로 이 표를 참조하기 위한 대상이다 — 아래 [슬롯이 두 곳에 있는 것을 DB 가 막는다] 참고.
- **마스터 데이터이므로 소프트 삭제를 두지 않는다.** 판매를 내릴 때는 `active` 를 내린다 — 이미 산 사람의 보유 행이 가리키는 대상이 사라지면 안 된다. 챌린지 마스터와 같은 방식이다.
- 시드는 `R__`(고정 id + upsert)로 넣는다. 매 배포마다 실행되므로 멱등해야 한다.
- **`currency` 를 아이템이 갖는다.** 상점이 "이 아이템은 어느 재화로 파는가"를 정하고, 지갑은 그것을 해석하지 않는다.

> **"인기" 뱃지는 컬럼으로 두지 않는다.** 판매량을 집계해 정하거나, 정 필요하면 운영이 `sort_order` 로 앞에 세운다. 캐시 컬럼을 두면 무효화 시점을 또 정해야 한다 — 챌린지 표지를 저장하지 않고 매번 계산하기로 한 것과 같은 기준이다.

### `member_avatar_item`

**누가 무엇을 갖고 있는가.** 영구 보유다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 보유 회원, `member.id` FK |
| avatar_item_id | BIGINT | N | 아이템, `avatar_item.id` FK |
| currency | VARCHAR(20) | N | **구매 시점** 결제 재화 |
| paid_price | INT | N | **구매 시점** 단가 |
| purchased_at | DATETIME(6) | N | 구매 시각 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- **`UNIQUE(member_id, avatar_item_id)` 가 중복 구매의 최종 방어선이다.** 애플리케이션 분기로만 막으면 따닥으로 들어온 두 요청이 모두 통과한다 — 이 저장소는 챌린지 참여·인증에서 이미 겪었고, **여기서는 재화가 두 번 빠지므로 더 나쁘다.**
- **구매 시점 단가와 재화를 스냅샷으로 남긴다.** 운영이 가격을 내리거나 결제 재화를 바꿔도 "얼마에 샀나"가 따라 움직이면 안 된다. 인증이 `participation_round` 를 스냅샷으로 갖는 것과 같은 이유다.
- 소프트 삭제를 두지 않는다. **보유는 취소되지 않는다** — 환불이 생기면 그때 별도 설계가 필요하다.
- `avatar_purchase_id` `BIGINT` `NULL` — 어느 구매에서 왔는지(아래). `avatar_purchase.id` FK, `ON DELETE RESTRICT`. 조회가 "이 구매의 아이템들" 이므로 인덱스는 `idx_member_avatar_item_purchase (avatar_purchase_id)`.
  - **`NULL` 은 "이 표가 생기기 전에 산 것" 하나만 뜻한다.** 새로 만드는 행은 애플리케이션이 반드시 채운다 — 비우면 구매 단위 연결이 다시 끊긴다.
  - **컬럼 자체는 `NULL` 을 허용한다.** 기존 행이 있어 `NOT NULL` 로 만들 수 없다. 제약이 못 지키는 만큼 **구매 경로가 한 곳(`ShopCommandService.purchase`)뿐이라는 사실**에 기댄다.
  - **기존 행은 백필하지 않고 `NULL` 로 둔다.** 묶을 근거가 `purchased_at` 타임스탬프뿐인데, 그것으로 구매를 가를 수 없다는 것이 이 표를 만드는 이유다. 같은 초에 두 구매가 들어왔으면 **잘못된 구매에 아이템이 붙는다** — 없는 것보다 나쁘다.
  - 백오피스는 `NULL` 을 "구매 이력 이전" 으로 표시한다. 운영에 22건뿐이라 이 예외가 오래 눈에 띄지 않는다.

### `avatar_purchase` — 설계안 (합의 필요)

**구매가 실체를 갖게 한다.** 지금은 여섯 개를 한 번에 사면 `member_avatar_item` 여섯 행과 `wallet_transaction` 한두 행으로 흩어지고, **이 둘을 하나의 구매로 묶는 단서가 `purchased_at` 타임스탬프뿐이다.**

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | `member.id` FK |
| idempotency_key | VARCHAR(64) | N | 클라이언트가 준 값 그대로. 요청 DTO 가 64자로 제한한다 |
| item_fingerprint | CHAR(64) | N | 아래 규칙으로 계산한 SHA-256 hex |
| purchased_at | DATETIME(6) | N | 구매 시각(KST) |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_avatar_purchase_idempotency` (`member_id`, `idempotency_key`)

#### 왜 두는가 — 멱등성만이 이유가 아니다

**멱등성만 필요했다면 표가 필요 없다.** 지갑 멱등 키에 장바구니 해시를 덧붙이는 것만으로 "같은 키로 다른 장바구니" 구멍은 닫힌다.

표를 두는 이유는 **백오피스가 요구하는 것이 구매 id** 이기 때문이다 — 구매 내역 조회, CS 문의 대조, 환불·회수가 전부 "이 구매" 를 가리킬 수 있어야 한다. 타임스탬프로 묶는 것은 같은 초에 두 구매가 들어오면 무너진다.

**지금이 제일 싸다.** 운영에 구매가 22건(구매자 4명)뿐이라 기존 행은 `NULL` 로 두거나 한 번에 백필할 수 있다. 나중에 붙이면 그때까지 쌓인 것을 타임스탬프로 묶어 소급 부여해야 한다.

#### 결제 금액을 여기 복사하지 않는다

`wallet_transaction` 이 이미 `(member_id, currency, "avatar:purchase:" + key)` 로 갖고 있다. **구매 행 + 그 아이템들 + 키로 찾은 지갑 거래 = 완전한 구매 결과**이므로, 스냅샷을 따로 저장하면 그것이 두 번째 진실이 되고 어긋났을 때 무엇이 맞는지 정할 방법이 없다.

> 단가와 재화는 이미 `member_avatar_item` 이 구매 시점으로 스냅샷한다. 여기서 다시 들 이유가 없다.

#### 선점이 먼저다

```text
회원 잠금
  ↓
INSERT avatar_purchase (member, key, fingerprint)     ← 여기서 선점
  ↓ 유니크 위반이면 기존 행을 읽는다
      fingerprint 같음  → 저장된 구매를 그대로 반환(200)
      fingerprint 다름  → 409 로 거절
  ↓
보유 저장(purchase_id 채움) → 재화별 차감
```

**`item_fingerprint` 가 없으면 키 재사용이 공짜 아이템이 된다.** 지갑은 같은 키를 "이미 처리한 요청" 으로 보아 **차감 없이 예전 결과를 돌려주는데**, 그 전제는 "같은 요청" 이다. 장바구니가 달라졌는데 키가 같으면 전제가 깨져 **보유 행만 생기고 값이 빠지지 않는다.**

#### fingerprint 는 계산법까지 정해 둔다

**"정렬한 id 의 해시" 만으로는 부족하다.** 구현이 조금만 달라도 같은 장바구니가 다른 값이 되어 **정상 재시도가 409 로 거절된다.** 그래서 규칙을 못박는다.

```text
1. itemIds 를 숫자로 오름차순 정렬한다        (문자열 정렬이 아니다 — "10" < "9" 가 된다)
2. 십진수로 찍어 쉼표로 잇는다                 "3,7,12"
3. UTF-8 로 인코딩해 SHA-256 → 소문자 hex 64자
```

**구분자가 없으면 안 된다.** 이어 붙이기만 하면 `[1, 23]` 과 `[12, 3]` 이 같은 입력이 된다.

**빈 장바구니와 중복 id 는 여기 오기 전에 거절된다.** 요청 DTO 가 `@NotEmpty` 로, `purchase` 가 `DUPLICATE_ITEM` 으로 막는다 — 수량 개념이 없어 같은 아이템을 두 번 담는 것은 뜻이 없다. 그래서 fingerprint 는 **중복 없는 비어 있지 않은 집합**만 다룬다.

**정렬하는 이유는 순서가 구매를 가르지 않기 때문이다.** 같은 아이템을 다른 순서로 보낸 것은 같은 구매다 — 응답의 아이템 순서만 요청 순서를 지킨다.

#### 선점부터 차감까지가 한 트랜잭션이다

**중간에 끊기면 재시도가 그것을 고치지 못한다.** `avatar_purchase` 와 보유 행만 커밋되고 차감이 실패한 상태에서 같은 키·같은 fingerprint 로 다시 들어오면, 위 규칙에 따라 **저장된 구매를 200 으로 돌려준다** — 빠진 차감을 복구하지 않고 "이미 처리됨" 으로 넘긴다. 아이템이 공짜가 되는 또 다른 길이다.

그래서 **구매 선점 · 보유 저장 · 재화별 차감과 잔액 갱신을 하나의 트랜잭션**에 둔다. 하나라도 실패하면 전부 되돌린다.

**재화가 여럿이어도 마찬가지다.** 파란 보석은 빠지고 주황 보석에서 막히면, 사용자는 한 번 결제한 줄 알면서 절반만 반영된 상태를 본다. `purchase` 가 이미 `@Transactional` 이고 차감 전에 모자란 재화를 **한꺼번에** 확인하는 것이 이 때문이다.

> 트랜잭션을 나눠야 할 만큼 커지면 그때는 `PENDING` 상태와 보상 처리가 필요하다. 지금 규모에서는 한 트랜잭션이 맞다.

**클라이언트가 키를 재사용하는 것은 막을 수 없다.** 그래서 서버가 "같은 키인데 다른 장바구니" 를 오류로 드러낸다. 조용히 둘 중 하나를 고르면 사용자가 산 것과 받은 것이 달라진다.

### `member_avatar_equipment`

**지금 무엇을 입고 있는가.** 슬롯당 한 행이다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 회원, `member.id` FK |
| slot | VARCHAR(30) | N | 슬롯. **아이템의 슬롯과 같아야 한다** |
| avatar_item_id | BIGINT | N | 착용 아이템, `avatar_item.id` FK |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- `UNIQUE(member_id, slot)` — 한 슬롯에 하나만 입는다.
- **슬롯을 컬럼이 아니라 행으로 둔다.** `background_item_id`·`hat_item_id` 처럼 컬럼으로 두면 슬롯이 하나 늘 때마다 `ALTER TABLE` 이 필요하다. 재화 종류를 행으로 둔 것과 같은 판단이다.
- **벗은 슬롯은 행이 없다.** `NULL` 을 넣지 않는다 — "안 입었다"와 "입었는데 값이 비었다"가 구분되지 않는다.
- 착용은 **보유한 아이템만** 가능하다. 서버가 `member_avatar_item` 을 확인한다.

#### 슬롯이 두 곳에 있는 것을 DB 가 막는다

`slot` 은 `avatar_item` 과 이 표에 **둘 다** 있다. 여기 두지 않으면 `UNIQUE(member_id, slot)` 을 걸 수 없어 "한 슬롯에 하나" 를 DB 로 강제하지 못한다 — 조인해야 알 수 있는 값에는 유니크를 걸 수 없다.

**그래서 중복을 남기되, 어긋날 수 없게 만든다.**

```sql
FOREIGN KEY (avatar_item_id, slot)
    REFERENCES avatar_item (id, slot)
```

아이템 id 만 참조하면 두 `slot` 이 서로 다른 값이 되어도 DB 는 모른다. 복합 FK 로 묶으면 **어긋난 조합을 넣는 것 자체가 실패**하고, **이미 착용된 아이템의 슬롯을 마스터에서 바꾸는 것도 막힌다.**

> 슬롯을 바꿔야 하면 **새 아이템을 만들고 옛것은 `active = 0` 으로 내린다.** 파는 물건의 자리가 바뀌는 것은 사실상 다른 물건이고, 이미 그것을 입고 있는 사람의 착용을 조용히 옮기는 것보다 낫다.

> **캐릭터 도메인이 여기서 생긴다.** `GroupResDTO.characterImageUrl` 과 `HomeResDTO` 의 "상단 유저 및 캐릭터 정보" 가 주석으로만 자리를 비워 두고 있는데, 이 표가 그 값들의 출처다.

### 슬롯

| 코드값 | 자리 |
| --- | --- |
| `HEAD` | 머리 |
| `BODY` | 몸 |
| `HAND` | 손 |

**셋은 서로 겹치지 않는다.** 한 아이템이 한 슬롯만 차지하므로, 무엇을 입으면 무엇이 벗겨지는지를 따로 정의할 필요가 없다 — 같은 슬롯의 이전 아이템이 교체될 뿐이다.

> **`FACE` 는 뺀다.** 처음에는 넷이었고 지금 코드·시드에 남아 있다. 의상이 전 캐릭터 공통 실루엣이라 얼굴 자리를 따로 두면 캐릭터 얼굴과 겹치는 그림이 되고, 자산이 그만큼 더 필요하다. **보유·착용 행이 운영에 0건이라 지금이 빼기 제일 싸다** — 산 사람이 생기면 복합 FK 가 걸린 행을 옮기는 일이 된다. 캐릭터 도메인 작업에서 함께 걷어낸다.

> **여러 슬롯을 한꺼번에 덮는 "세트" 는 두지 않는다.** 세트가 생기면 "이 아이템이 어떤 슬롯들을 점유하는가" 를 데이터로 들고 충돌을 정리해야 하는데, 지금 구성에는 그런 아이템이 없다. 나중에 생기면 그때 연결 표를 더한다.

`slot` 은 코드값만 갖고 화면 문구는 클라이언트가 붙인다 — 재화 이름·스트릭 문구와 같은 기준이다.

**슬롯이 늘어도 스키마는 바뀌지 않는다.** 새 자리가 생기면 `slot` 값 하나와 아이템 시드만 더하면 된다. 슬롯을 컬럼이 아니라 행으로 둔 이유다.

#### 화면의 "전체" 탭은 슬롯이 아니다

탭은 **전체 · 머리 · 몸 · 손** 넷이지만 **슬롯은 셋이다.** `전체` 는 분류가 아니라 **필터 없음**을 뜻한다 — 목록 요청에서 `slot` 을 생략하면 전부 내려간다.

```text
GET /api/shop/items            전체
GET /api/shop/items?slot=HEAD  머리
```

**`ALL` 을 슬롯 값으로 두지 않는다.** 두면 `avatar_item.slot` 에 `ALL` 이 들어갈 수 있게 되고, 착용에서 "모든 자리를 차지하는 아이템" 이라는 뜻이 되어 버린다. 챌린지 분류 칩에서 `전체` 를 분류로 두지 않은 것과 같은 기준이다.

### 목록은 파는 것과 가진 것을 함께 내린다

```text
GET /api/shop/items?slot=HEAD
```

- **판매 중(`active = 1`) 인 아이템** — 살 수 있는 것
- **보유한 아이템은 `active` 와 무관하게 포함** — 판매가 내려가도 이미 산 것은 계속 보이고 입을 수 있어야 한다

**"활성만" 으로 좁히면 판매 중단된 보유 아이템이 목록에서 사라진다.** 그러면 사용자는 가진 것을 화면에서 고를 수 없고, 착용 저장은 전체 착장을 받으므로 **그 아이템이 목록에 없다는 이유로 조용히 벗겨진다.** 보유는 판매 여부와 무관하다.

보유한 것도 같은 격자에 섞여 나오므로 응답에 `owned` 를 싣는다 — 화면이 목록 하나다.

### 미리보기는 서버를 거치지 않는다

목록 응답이 이미 아이템 이미지를 내려주므로, **안 산 아이템을 눌러 캐릭터에 입혀 보는 것은 클라이언트가 한다.** 서버에 미리보기 상태를 저장하지 않는다 — 저장하면 "입어 본 것"과 "입은 것"이 두 벌로 생기고, 어느 쪽이 진짜인지 화면마다 갈린다.

**서버가 아는 것은 보유와 착용 둘뿐이다.**

### 구매는 여러 개를 한 번에, 그리고 입히지 않는다

화면이 "6개 · 4,800" 처럼 묶어서 결제한다. 요청은 **아이템 id 목록과 멱등 키**뿐이다 — 가격과 결제 재화는 `avatar_item` 이 갖고 있으므로 **클라이언트가 보낸 값을 믿지 않는다.**

```text
POST /api/shop/items/purchase   { itemIds, idempotencyKey }

⓪ 회원 잠금    FOR UPDATE
① 요청 검증    빈 목록·중복·없는 것·판매 종료·이미 보유
② 구매 선점    avatar_purchase (아래 절)
③ 잔액 확인    모자란 재화를 한꺼번에
④ 보유 생성    구매 시점 단가·재화를 스냅샷, purchase_id 를 채운다
⑤ 재화 차감    재화별로 나눠
```

**전부 한 트랜잭션이다.** 나뉘면 재화만 빠지고 아이템이 안 생기거나, 절반만 반영된 상태가 남는다. **재화가 여럿일 때 일부만 성사되는 것도 같은 문제다** — 사용자는 한 번 결제한 줄 안다.

**보유 행이 차감보다 먼저인 것이 중요하다.** 따닥으로 두 번 들어오면 `UNIQUE(member_id, avatar_item_id)` 가 하나를 먼저 떨어뜨리고, **진 쪽은 차감까지 가지 못한다.** 순서를 뒤집으면 둘 다 차감을 시도한 뒤에야 하나가 걸린다. 리워드 지급이 지급 행을 먼저 만드는 것과 같은 이유다.

**판매 종료 확인이 없으면 판매를 내려도 계속 팔린다.** `active` 는 목록에서 빼는 값이 아니라 **파는가 마는가**이므로, 아이템 id 를 아는 클라이언트는 목록을 거치지 않고 바로 구매를 부를 수 있다.

**재화가 섞여도 된다.** 상점에 두 재화가 섞여 있어, 한 재화로 통일해야만 살 수 있게 하면 사용자가 고른 것을 그대로 살 수 없다. 재화별로 합계를 내어 각각 차감한다.

**모자란 재화는 차감을 시작하기 전에 전부 확인한다.** 그냥 시도하면 먼저 걸린 재화 하나만 알게 되어, 사용자가 그것을 채우고 돌아왔을 때 다른 재화로 또 막힌다.

**중복 아이템을 조용히 합치지 않는다.** 착용은 두 번 보내도 뜻이 분명하지만, 구매는 화면이 이미 개수와 금액을 보여준 뒤에 들어온다. 서버가 말없이 하나를 지우면 **사용자가 본 금액과 실제 결제액이 어긋난다.**

#### 사고 입히지 않는다

**같은 자리 아이템을 둘 이상 함께 사면 어느 쪽을 입힐지 정할 수 없다.** 서버가 임의로 고르면 사용자가 고르지 않은 착장이 저장되므로, 착용은 아래 착장 저장이 맡는다.

> 단일 구매 시절에는 산 아이템을 그 자리에서 입혔다. 묶음 구매가 되면서 그 규칙이 성립하지 않는다.

**이미 가진 아이템은 살 수 없다.** `UNIQUE(member_id, avatar_item_id)` 가 막고, 재화도 빠지지 않는다. 갈아입기는 아래 착용 저장으로 한다.

### 착용 저장은 착장 전체를 한 번에 받는다

구매를 거치지 않고 **이미 가진 것끼리 갈아입는** 경로다. 클라이언트에서 이것저것 바꿔 보다가 마음에 드는 착장이 되면 그때 한 번 저장한다.

```text
PUT /api/members/me/avatar
{ "itemIds": [12, 47, 3] }
```

**아이템 id 만 받는다.** 슬롯은 `avatar_item` 이 갖고 있으므로 서버가 알아낸다 — 클라이언트가 슬롯을 함께 보내면 그 값과 아이템의 실제 슬롯이 어긋날 수 있고, 그때 무엇을 믿을지 또 정해야 한다.

#### 요청에 없는 슬롯은 벗는다

**보낸 것이 곧 전체 착장이다.** 목록에 없는 슬롯은 비운다.

```text
지금        HEAD=12  HAND=47  BODY=88
요청        [12, 47]
저장 후      HEAD=12  HAND=47            ← BODY 는 벗겨진다
```

"지금 화면에 보이는 그대로 저장" 이 되어 **벗기기를 위한 별도 표현(`null` · 빈 값)이 필요 없다.** 대신 클라이언트가 바뀐 것만 보내면 나머지가 벗겨지므로, **항상 전체를 보내야 한다**는 것이 계약이다.

> 부분 갱신으로 두면 벗기기를 표현할 방법을 따로 만들어야 하고(`{"BODY": null}` 같은), 그러면 "안 보냈다" 와 "비우라고 보냈다" 를 가르는 규칙이 하나 더 생긴다.

#### 서버가 확인하는 것

- **전부 보유한 아이템인가** — 하나라도 아니면 **전체를 거절한다.** 미보유 착용은 구매를 건너뛰는 길이 된다
- **같은 슬롯이 두 번 오지 않았는가** — 오면 어느 쪽을 입힐지 알 수 없으므로 거절한다
- **판매 중단(`active = 0`) 아이템은 어떻게 하나** — 이미 산 것이므로 **계속 입을 수 있다.** 판매를 내리는 것과 보유를 뺏는 것은 다르다

**부분 성공은 없다.** 하나가 걸리면 착용 상태는 그대로 남는다.

#### 지우고 넣는 것이 한 트랜잭션이고, 회원 단위로 잠근다

전체 교체는 **기존 행을 지우고 새 행을 넣는** 두 단계다. 그 사이에 오류가 나면 **일부 슬롯만 저장된 착장**이 남는다 — 사용자가 저장을 눌렀는데 절반만 입혀진다.

```text
member 행을 잠근다   (SELECT ... FOR UPDATE)
  → 보유·슬롯 검증
  → 기존 착용 행 삭제
  → 새 착용 행 삽입
커밋
```

**같은 회원의 동시 저장도 잠금으로 직렬화한다.** 안 잠그면 두 요청의 삭제와 삽입이 섞여 **어느 쪽에도 없던 조합**이 남을 수 있다. `UNIQUE(member_id, slot)` 이 일부는 막지만, 한쪽이 지운 뒤 다른 쪽이 넣는 순서에서는 제약에 걸리지도 않는다.

> **실측하니 잠금을 빼도 섞이지 않았다.** `DELETE ... WHERE member_id = ?` 가 이미 행을 잠가 사실상 직렬화되기 때문으로 보인다. 그래도 **잠금을 남긴다** — 그 성질은 격리 수준과 삭제 방식에 딸린 것이라, 나중에 벌크 삭제로 바꾸거나 격리 수준을 낮추면 조용히 사라진다. **동시성을 DB 구현 세부에 기대지 않는다.**

**나중에 온 요청이 이긴다.** 착용은 "지금 이 착장으로 해 달라"는 요청이라, 충돌을 거절하는 것보다 마지막 것을 적용하는 편이 사용자의 뜻에 가깝다. 재화가 오가지 않아 되돌릴 것도 없다.

> 잠글 부모 행이 따로 없어 `member` 를 잠근다. 아바타 저장은 드문 요청이라 이 잠금이 경합을 만들 일은 거의 없다 — 신고 판정에서 인증 행을 잠근 것과 같은 판단이다.

**구매(`POST .../purchase`) 도 같은 잠금을 쓴다.** 구매가 착용까지 바꾸므로, 저장과 동시에 들어오면 같은 슬롯을 서로 덮는다.

### 기본 제공 아이템은 없다

가입하면 **아무것도 입지 않은 상태**다. `member_avatar_equipment` 에 행이 하나도 없는 것이 정상이고, 응답은 슬롯별로 비어 있다.

**가입 흐름을 건드리지 않는다.** 지갑을 가입 시점에 만들지 않고 필요할 때 만드는 것과 같은 방식이다.

## 캐릭터 도메인 테이블 — 설계안 (합의 필요)

아바타 아이템이 "입는 것"이라면 캐릭터는 **그것을 입는 몸**이다. 슬롯 셋(`HEAD`·`BODY`·`HAND`)은 그대로 두고 그 아래 베이스로 들어간다.

**`AvatarSlot` 에 `CHARACTER` 를 더하지 않는다.** 넣으면 `avatar_item` 한 표에 **돈으로 사는 것과 조건으로 여는 것이 섞이고**, 슬롯 유니크 제약(`UNIQUE(member_id, slot)`)이 "캐릭터는 반드시 하나"와 "아이템은 없을 수 있다"를 같은 규칙으로 다루게 된다. 성질이 다르므로 표를 나눈다.

**배경은 이번 범위에 없다.** 표도 슬롯 값도 만들지 않는다 — 자산이 없고 화면도 없는 자리를 미리 파 두면, 쓰지 않는 구조를 계속 유지·설명하게 된다. 필요해지면 `slot` 값 하나와 시드만 더하면 되므로 미리 만들어 둘 이유가 없다.

### 화면은 그림 한 장이 아니라 레이어를 겹친 것이다

캐릭터는 **둥지 사이에 끼어** 그려진다. 아래가 먼저, 위가 나중이다.

```text
60  소품        avatar_item (HAND)
50  머리장식     avatar_item (HEAD)
40  윗둥지       둥지의 앞면 — 캐릭터를 가린다
30  옷          avatar_item (BODY)
20  캐릭터       character
10  뒷둥지       둥지의 뒷면
```

**`z` 는 아이템이 아니라 자리에 붙는다.** 아이템마다 깊이를 정하게 두면 같은 옷끼리도 앞뒤가 갈려 조합이 깨진다. 자리마다 하나로 고정한다.

**간격을 10 으로 띄운다.** 사이에 레이어가 하나 생겨도 기존 값을 다시 매기지 않는다 — 자산·앱·DB 가 같은 숫자를 보고 있으므로 전 행을 옮기는 일이 제일 비싸다.

#### 응답은 "무엇을 입었나"가 아니라 "이 순서로 그려라"다

지금 응답은 `{slot, imageUrl}` 목록이고 **순서가 없다.** 프론트가 슬롯 이름을 보고 어디에 그릴지 정하고 있다는 뜻이다. 그러면 **레이어를 하나 더하는 순간 앱 배포가 필요하다.**

```json
{
  "layers": [
    { "z": 10, "layer": "NEST_BACK", "imageUrl": "..." },
    { "z": 20, "layer": "CHARACTER", "imageUrl": "..." },
    { "z": 30, "layer": "BODY",      "imageUrl": "..." }
  ]
}
```

**칸 이름은 `slot` 이 아니라 `layer` 다.** 둥지와 캐릭터는 아이템이 차지하는 자리(`AvatarSlot`)가 아니라서 `slot` 으로 부르면 `avatar_item` 의 슬롯과 같은 것으로 읽힌다. 값은 겹치지만(`BODY`·`HEAD`·`HAND`) 뜻이 다른 열거형이다.

계약은 넷이다.

| | |
| --- | --- |
| **배열은 이미 정렬돼 있다** | 받은 순서대로 겹쳐 그리면 끝이다 |
| **모르는 `layer` 가 와도 그냥 그린다** | 이름으로 분기하지 않는다. 새 레이어를 서버만 고쳐 넣을 수 있는 유일한 방법이다 |
| **`z` 는 확인용으로 같이 내린다** | 프론트가 `z` 로 다시 정렬해도 결과가 같아야 한다 |
| **안 입은 자리는 실리지 않는다** | 빈 자리를 `null` 로 채우지 않는다. 착용 응답과 같은 규칙이다 |

**캐릭터는 항상 실린다.** 조건 행이 없는 기본 캐릭터가 있어 착용 아이템이 하나도 없어도 배열이 비지 않는다.

**그 전제는 보유 행이 모두에게 있다는 것이다.** 가입 흐름이 해금 판정을 한 번 돌려 기본 캐릭터를 넣고, 그 경로가 생기기 전에 가입한 회원은 백필 마이그레이션(`V20260818120000__backfill_default_character.sql`)이 채웠다.

**전제가 깨지면 `layers` 를 통째로 비운다.** 둥지만 두면 빈 둥지가 남고 아이템만 두면 허공에 모자가 뜬다 — 둘 다 "무언가 잘못됐다" 로 보이는 화면이라 차라리 비운다. 여기 걸리는 사람은 없어야 하고, 걸린다면 그것이 신호다.

#### 겹쳐 그리려면 모든 장이 같은 캔버스여야 한다

**서버는 위치를 내리지 않는다.** 레이어마다 `x`·`y`·크기를 내리기 시작하면 자산을 다시 그릴 때마다 그 값이 전부 낡고, 앱은 그 값을 믿고 배치한 뒤라 자산 교체가 앱 배포를 부른다.

대신 **모든 레이어를 같은 크기 캔버스에 투명 여백 포함해서 그린다.** 그러면 프론트는 전부 같은 자리에 겹쳐 놓기만 하면 되고, 자산이 바뀌어도 서버·앱이 할 일이 없다. 이건 디자인 쪽과 맞춰야 하는 규격이다 — **자산 제작 전에 정해야 한다.**

### 둥지는 사는 것이 아니라 기록의 결과다

**전 캐릭터·전 사용자가 같은 둥지를 쓴다.** 캐릭터마다 다르지 않으므로 마스터 표가 필요 없다 — 자산은 **레벨 둘 × 앞뒤 두 장 = 넉 장**이고 key 는 설정값으로 둔다. 한 물건이 캐릭터를 사이에 두고 두 번 그려지지만, **레이어 둘로 나오는 것일 뿐 소유·구매 단위가 아니라서** 아이템 표를 건드리지 않는다.

| 레벨 | 조건 |
| --- | --- |
| 1 | 기본 |
| 2 | **15일 연속, 그날 예정된 개인 루틴을 하나도 빠짐없이 완수** |

레벨이 오르면 **형태가 바뀐다** — 색이나 장식이 아니라 다른 그림이다. 레이어 자리(`10` 뒤 · `40` 앞)는 그대로다.

#### 레벨을 저장하지 않는다

**강등이 있다.** 하루라도 비면 연속이 끊겨 1일부터 다시 센다. 그래서 레벨은 **기록을 보고 계산하는 값**이지 들고 다니는 상태가 아니다.

```sql
SELECT COUNT(*) FROM member_activity_day
WHERE member_id = :memberId
  AND all_completed = 1
  AND activity_date BETWEEN :yesterdayKst - INTERVAL 14 DAY AND :yesterdayKst
```

**기준일을 애플리케이션이 넘긴다.** `CURRENT_DATE` 를 쓰면 DB 세션의 시간대가 기준이 되는데, 이 표의 `activity_date` 는 KST 로 찍힌 값이다. 둘이 다르면 자정 언저리에 하루가 밀려 **연속이 끊긴 것처럼 보인다.** 인증 날짜를 서버의 KST 오늘로 정하는 것과 같은 기준이다.

**15면 레벨 2, 아니면 1이다.** 중간에 하루가 비면 15일 창에 15개가 들어올 수 없으므로 **끊김을 따로 판정하지 않는다.** 연속일수 컬럼도, 그것을 리셋하는 배치도 두지 않는다 — **매일 자정에 도는 일이 없으므로 배치가 죽어 있는 동안 조용히 틀리는 상태가 생기지 않는다.**

#### 창은 오늘이 아니라 어제까지다

**오늘을 창에 넣으면 매일 아침 강등된다.** 창 길이와 임계값이 같아서 오늘 몫이 아직 안 끝난 시간대에는 언제나 하나가 모자라기 때문이다. 레벨 2 를 유지하던 사람의 둥지가 **자정에 쪼그라들었다가 그날 루틴을 끝내면 돌아온다** — 하루도 빠뜨리지 않았는데도 그렇다.

어제까지만 보면 어제 시점의 판정이 그날 하루 동안 그대로 유지된다.

**대신 보상이 하루 늦다.** 15일째를 끝낸 그날 저녁이 아니라 **다음 날부터** 레벨 2 다. 둥지가 하룻밤 사이에 자라는 셈이라 오히려 자연스럽고, 매일 아침 벌 받는 것보다 낫다고 봤다.

> **창을 16일로 넓혀 15개를 요구하는 방법도 있다.** 그러면 그날 저녁에 바로 레벨 2 가 된다. 대신 **중간에 하루가 비어도 통과**하므로 "창이 가득 찼다는 것이 곧 연속" 이라는 성질이 깨지고, 연속을 따로 판정해야 한다. 그 성질을 지키는 쪽을 골랐다.

**일수는 설정값으로 둔다.** 캐릭터 조건이 `target_count` 로 빠져 있는 것과 같은 이유다 — 실사용 데이터가 없어 지금 이 숫자를 맞힐 방법이 없다. 둥지는 마스터 표가 없으므로 시드가 아니라 설정에 둔다.

> **강등이 있으면 목표가 길수록 실수 한 번이 비싸다.** 처음 잡았던 30일은 29일째에 끊기면 29일이 사라지고, 그것이 두어 번 반복되면 조건이 "꾸준함에 대한 보상" 이 아니라 "한 번도 안 틀린 사람만" 이 된다. 15일은 다시 붙는 것이 현실적인 길이다.

> 카운터를 두면 "기록" 과 "카운터" 라는 진실이 둘이 되고, 어긋났을 때 무엇이 맞는지 정할 방법이 없다. 그룹 스트릭이 `current_streak` 컬럼을 들고 배치로 리셋하는 방식인데, 둥지는 그 길을 가지 않는다.

#### 판정은 그날 해야 한다 — 나중에는 복원할 수 없다

**개인 루틴은 과거에 무엇이 예정돼 있었는지 알 수 없다.**

| 무엇이 | 어떻게 사라지는가 |
| --- | --- |
| 반복 요일 | 루틴을 수정·삭제하면 `member_routine_schedule` 행이 **물리 삭제**된다(`orphanRemoval`) |
| 루틴 자체 | 삭제는 `active = false` 이고 **삭제 시각을 남기지 않는다** |
| 그날의 예정 목록 | 애초에 날짜별 행이 없다. 요일 패턴뿐이다 |

그래서 **하루가 지난 뒤 배치로 소급 계산하면 안 된다.** 그 사이 루틴을 하나 지운 사람은 어제의 판정이 바뀐다. **인증이 들어온 그 자리에서 판정하고 행으로 남긴다** — 그 순간이 예정 목록을 아는 유일한 시점이다.

**되돌릴 일은 없다.** 개인 루틴 인증에는 삭제 API 가 없고 소프트 삭제 컬럼도 없다 — 한 번 인증하면 취소되지 않으므로, 성공으로 기록한 날이 나중에 실패로 바뀌지 않는다.

#### 성공한 날만 기록한다

**실패를 행으로 남기지 않는다.** 성공한 날에만 표시가 남고, 없는 날은 전부 실패다. 그러면 **루틴이 0개인 날은 아무것도 안 해도 저절로 실패**가 되어 규칙이 하나 줄어든다 — 성공으로 세면 루틴을 전부 지워 두고 15일을 채울 수 있고, 조건이 묻는 "꾸준히 했다" 와 뜻이 반대가 된다.

**그날 예정이 0개인 날도 실패다.** 개인 루틴은 요일 패턴이라(`member_routine_schedule` 에 요일 행이 있고 "매일" 은 7행이다) 주 5일로 잡으면 주말에 예정이 없다. 그런 날도 연속이 끊긴다.

따라오는 결과를 적어 둔다 — **레벨 2 는 사실상 7요일을 다 덮은 사람의 것이다.** 주말을 비운 사람은 최대 5일에서 멈춘다. 조건을 "빠짐없이" 로 읽은 결과이고, 완화하려면 일수가 아니라 **이 규칙**을 고쳐야 한다(일수를 낮춰도 막히는 지점은 같다).

### 잠금이 곧 알이다

캐릭터 한 마리가 이미지 두 장을 갖는다. **알은 성장 단계가 아니라 잠금 상태의 그림이다.**

```text
잠겨 있음  →  알 그림
해금 조건 달성  →  성체
```

**관문은 하나다.** 조건을 채우면 그 자리에서 성체가 된다 — 기다리는 구간이 없다.

> 중간 단계도, 부화도 두지 않는다. 난이도는 **해금 조건만으로** 잡는다.

### `avatar_character`

앱이 제공하는 마스터 데이터다. `R__` 시드가 단일 진실 공급원이며 id 는 `1~999` 대역에 고정한다.

> **표 이름이 `character` 가 아니다.** MySQL 예약어라 백틱 없이는 `CREATE` 도 `SELECT` 도 실패한다. 감쌀 수는 있지만 시드·네이티브 쿼리 어디서든 한 번만 빠뜨리면 터지고, 자바에서도 클래스 이름이 `java.lang.Character` 와 겹친다. 자산 prefix 가 이미 `avatar/` 하나로 모여 있어 이름을 그쪽에 맞췄다. **참조 컬럼은 `character_id` 그대로다.**

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키. 시드가 직접 지정한다 |
| code | VARCHAR(30) | N | 논리 키. 시드·조건이 참조한다 |
| name | VARCHAR(30) | N | 이름 |
| egg_image_key | VARCHAR(512) | N | **잠긴 상태**로 보여줄 알 그림. 절대 URL 이 아니라 S3 key. **목록의 잠금 표시 전용이고 해금 뒤에는 어디에도 쓰지 않는다** |
| adult_image_key | VARCHAR(512) | N | **해금 뒤** 보여줄 성체 그림 |
| hidden | TINYINT(1) | N | 목록에 감출지. **지금 시드는 전부 `0` 이다**(아래) |
| display_order | INT | N | 노출 순서 |
| active | TINYINT(1) | N | 사용 여부, 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_avatar_character_code` (`code`)

**등급 컬럼은 두지 않는다.** 기획에 등급 넷(기본·레어·에픽·특별)이 언급돼 있으나 화면에서 쓰는 곳이 정해지지 않았고, 캐릭터는 파는 것이 아니라 조건으로 여는 것이라 **난이도를 이미 해금 조건이 표현한다.** 필요해지면 그때 컬럼 하나를 더한다.

**수집 속도를 정하는 것은 오직 해금 조건이다.** 기다리는 구간이 없으므로 조건 하나하나가 곧 난이도다. 첫 캐릭터를 이른 시점에 열어 주는 것이 중요하다 — 그전까지 목록이 알로만 차 있으면 **이 시스템이 무엇인지 알려 주는 순간이 오지 않는다.**

조건 수치는 `character_unlock_condition.target_count` 에 있어 **`R__` 시드 한 줄을 고치고 배포하면 조정된다.** 실사용 데이터가 없어 지금 그 값을 맞힐 방법이 없으므로, 코드가 아니라 데이터에 두는 것이 중요하다.

### `character_unlock_condition`

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| character_id | BIGINT | N | `avatar_character.id` FK |
| condition_key | VARCHAR(30) | N | 판정기 종류(아래) |
| condition_param | VARCHAR(255) | Y | 키마다 뜻이 다르다. 없으면 `NULL` |
| target_count | INT | N | 목표 수치 |
| sort_order | INT | N | 진행도를 조건별로 표시하기 위한 순서 |

**한 캐릭터에 조건 행이 여럿이면 AND 다.** 그래서 "운동 **또는** 건강"을 조건 둘로 나누면 안 된다 — 둘 다 채워야 하는 뜻이 되어 버린다. 합집합은 한 행의 `condition_param` 에 쉼표로 나열해 표현한다.

**조건 행이 하나도 없으면 항상 열려 있는 기본 캐릭터다.** 팝업도 띄우지 않는다.

#### 판정기 여덟

| `condition_key` | 무엇을 세는가 | `condition_param` |
| --- | --- | --- |
| `ACTIVE_DAYS` | 활동한 날 수 | 없음 |
| `CATEGORY_DAYS` | 그 카테고리를 한 날 수 | 논리 키. 쉼표면 합집합 |
| `TIME_WINDOW_DAYS` | 그 시간대에 완료한 날 수 | `HH:MM-HH:MM` (KST) |
| `ALL_KINDS_DAYS` | 하루에 개인·모임·챌린지를 모두 한 날 수 | 없음 |
| `ROUTINE_STREAK` | 한 개인 루틴을 예정일마다 연속 완료 | 없음 |
| `STREAK_DAYS` | 활동일 **연속** | 없음 |
| `LIKE_GIVEN` | 지금 좋아요를 눌러 둔 서로 다른 인증 수 | 없음 |
| `DEADLINE_RUSH` | 마감까지 남은 시간 안에 완료 | 초 |

**`STREAK_DAYS` 와 `ACTIVE_DAYS` 는 다르다.** 앞은 하루라도 비면 0 으로 돌아가고, 뒤는 줄지 않는다. 같은 100 이라도 난이도가 전혀 다르므로 시드에서 헷갈리면 안 된다.

**`ROUTINE_STREAK` 은 인증이 들어온 그 자리에서 세야 한다.** 개인 루틴은 과거에 무엇이 예정돼 있었는지 복원할 수 없어(요일 행이 물리 삭제된다) "예정일에 빠뜨렸는가" 를 나중에 판정할 수 없다.

#### 조건은 서버 혼자 판정할 수 있는 것만 쓴다

**앱의 협조가 필요한 조건은 두지 않는다.** 기획 원안에 *"자정에 앱을 켜 두고 있기"*, *"알림을 눌러 들어오기"* 같은 것이 있었는데, 서버는 그런 일이 있었는지 알 방법이 없다. 넣으려면 클라이언트가 새 이벤트를 쏘고 그것을 받는 표를 만들어야 하고, **조건 하나가 프론트 일정을 물고 들어온다.** 판정에 필요한 값은 이미 쌓이는 기록(인증·활동일·좋아요) 안에서만 고른다.

실제로 둘을 바꿨다. **성격은 살리고 판정 근거만 옮겼다.**

| | 원안 | 지금 |
| --- | --- | --- |
| 모리 | 자정 00:00 에 접속해 있기 | `TIME_WINDOW_DAYS` `00:00-00:59` **3일** — 한밤중에 완료 |
| 까루 | 알림을 눌러 접속 20회 | `ALL_KINDS_DAYS` **10일** — 하루에 셋 다 완료 |

같은 이유로 **노아는 시간대로 읽었다.** 기획서 세부에 *"기상·아침 성격 루틴"* 이 있었으나 **루틴의 성격은 서버가 알 수 없다** — 이름으로 추측할 수 없으므로 표에 적힌 `05:00-07:59` 를 근거로 삼는다.

**히든 캐릭터를 두지 않는다.** `hidden` 컬럼은 남기되 시드는 전부 `0` 이다 — 조건을 감추면 사용자가 무엇을 해야 하는지 알 수 없고, 그러면 목록이 열리지 않는 알로 남는다. **감추더라도 조건 자체는 쉬운 것으로 한다.** 어려운 조건과 감춰진 조건이 겹치면 그 캐릭터는 사실상 아무도 못 여는 그림이 된다.

#### `LIKE_GIVEN` 은 누적이 아니라 **지금 눌러 둔 수**다

원안은 "좋아요를 N 번 눌렀다" 였는데 **그렇게는 셀 수 없다.** 좋아요 취소는 하드 삭제이고(위 [좋아요] 절 — 소프트 삭제를 쓰면 다시 누를 때 유니크에 걸린다) 취소한 행은 사라진다. 누적을 세려면 좋아요 도메인에 이력 표를 새로 만들어야 하는데, **캐릭터 하나 때문에 남의 도메인을 건드리는 값이 아니다.**

**그래서 뜻을 바꾼다 — "서로 다른 인증 N 건에 좋아요가 눌려 있다."** 지금 남아 있는 행을 세는 것이라 표를 새로 만들 필요가 없다.

```sql
SELECT COUNT(*) FROM challenge_verification_like WHERE member_id = :memberId
-- + group_routine_verification_like 를 같은 방식으로 합산
```

**눌렀다 취소하기를 반복해도 늘지 않는다.** 취소가 행을 지우므로 N 개를 **동시에 유지**해야 하고, 원안이 막으려던 "따닥 반복으로 채우기" 가 규칙 없이 막힌다.

**조건을 채운 뒤 취소해도 캐릭터는 그대로다.** 해금은 되돌리지 않는다 — `member_character` 에 행이 생기면 끝이다(둥지와 다른 점이다. 둥지는 강등이 있다).

#### `condition_param` 에는 카테고리 id 가 아니라 논리 키를 넣는다

카테고리가 세 곳에 따로 있고 **id 가 서로 다르다.**

| 어디 | 무엇 | 모양 |
| --- | --- | --- |
| 개인 루틴 | `routine_category` | 전역 프리셋 6종(`member_id IS NULL`) + 사용자 생성 |
| 그룹 루틴 | `group_routine_category` | 전역 프리셋 6종(`group_id IS NULL`) + 그룹 생성 |
| 챌린지 | `ChallengeCategory` | enum |

코드가 논리 키 하나를 세 곳에 매핑한다. **사용자가 만든 동명 카테고리는 세지 않는다** — 운영 DB 에 실제로 `[테스트]사이드` 같은 사용자 생성 카테고리가 있다.

### `member_character`

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | `member.id` FK |
| character_id | BIGINT | N | `avatar_character.id` FK |
| unlocked_date | DATE | N | 해금된 날(KST) |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_member_character` (`member_id`, `character_id`)

**행이 있으면 해금된 것이고, 없으면 알이다.** 상태 컬럼을 따로 두지 않는다 — 전이가 한 방향뿐이라 행의 존재가 곧 상태다.

**유니크가 해금의 멱등을 보장한다.** 판정이 두 번 돌아도 두 번째 INSERT 가 막힌다 — 이 프로젝트가 인증 중복을 다루는 방식과 같다. 애플리케이션 검사는 선검사이고 최종 판정은 제약이 한다.

### `member_selected_character`

**보유와 선택은 다른 사실이다.** 여러 마리를 모으므로 "지금 어느 캐릭터를 쓰고 있는가" 가 따로 있어야 한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| member_id | BIGINT | N | **기본 키.** `member.id` FK |
| character_id | BIGINT | N | 고른 캐릭터 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

```sql
PRIMARY KEY (member_id)
FOREIGN KEY (member_id, character_id)
    REFERENCES member_character (member_id, character_id)
```

**`member_character` 에 `selected` 플래그를 두지 않는다.** MySQL 에는 부분 유니크가 없어 "회원당 선택 하나" 를 제약으로 만들 수 없다 — 애플리케이션이 지키는 규칙이 되고, 따닥으로 들어온 두 요청이 둘 다 통과하면 선택이 둘이 된다. 표를 나누면 **기본 키가 그것을 공짜로 보장한다.**

**잠긴 캐릭터를 고를 수 없다는 것도 제약이 막는다.** 복합 FK 가 `member_character` 를 참조하므로 **보유 행이 없으면 INSERT 자체가 실패한다** — 알 상태인 캐릭터는 고를 수 없다. 착용이 `(avatar_item_id, slot)` 복합 FK 로 어긋난 조합을 막는 것과 같은 방식이다.

**그래서 기본 캐릭터도 보유 행을 갖는다.** "조건 행이 0개면 열려 있다" 를 행 없이 특례로 두면, 그 캐릭터만 복합 FK 를 통과하지 못해 고를 수 없게 된다. 가입 직후 해금 판정을 한 번 돌리면 조건 0개는 그 자리에서 통과하므로 특례가 필요 없다 — **보유는 언제나 행이다.**

**가입 트랜잭션이 보유와 선택을 함께 만든다.** 보유만 만들고 선택을 비워 두면 가입 직후 화면이 "선택된 캐릭터 없음" 상태가 되고, 그 상태를 어떻게 그릴지 규칙이 하나 더 필요해진다. 둘을 같은 트랜잭션에 두면 **회원에게 선택된 캐릭터가 항상 있다**가 계약이 된다.

> 만드는 순서는 `member_character` → `member_selected_character` 다. 복합 FK 가 보유 행을 요구하므로 뒤집으면 실패한다.

> 조건 0개 캐릭터를 나중에 추가하면 기존 회원에게는 행이 없다. 그때는 시드와 함께 백필한다.

### `member_activity_day`

**해금 조건이 "며칠 했는가" 를 묻는데, 지금은 그것을 셀 방법이 없다.** 스트릭은 그룹·챌린지 단위로만 있고 전역 활동일 기록이 없다. `ACTIVE_DAYS` 가 이 표 위에서 성립한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | `member.id` FK |
| activity_date | DATE | N | 루틴을 한 날(KST) |
| all_completed | TINYINT(1) | N | **그날 예정된 개인 루틴을 전부 완수했는가.** 둥지가 이 값을 센다 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

유니크: `uk_member_activity_day` (`member_id`, `activity_date`)

**캐릭터를 가리키는 칸이 없다.** 그냥 "이 사람이 이날 루틴을 했다"이다. 조건 판정이 이 위에서 센다.

**`all_completed` 를 별도 표로 빼지 않는다.** 축이 `(회원, 날짜)` 로 같아서 표를 나누면 같은 키를 두 번 쓰고 두 곳이 어긋날 자리가 생긴다. 뜻은 둘이 다르다 — 행의 존재는 "뭐라도 했다"(개인·그룹·챌린지 합산), `all_completed` 는 **개인 루틴만** 보고 "예정된 것을 전부 했다"이다.

#### 행 만들기와 `all_completed` 갱신은 다른 일이다

**"중복이면 조용히 무시" 로 끝내면 안 된다.** 행의 존재는 유니크가 지켜 주지만 `all_completed` 는 갱신이 필요하다. 이 순서가 실제로 생긴다.

```text
09:00  챌린지 인증        → 행 INSERT (all_completed = 0)
21:00  마지막 개인 루틴 완료 → INSERT 가 유니크에 튕김 → 무시
                            all_completed 가 0 인 채로 남는다
```

그래서 **upsert 로 쓰고, 값은 한 방향으로만 움직인다.**

```sql
INSERT INTO member_activity_day (member_id, activity_date, all_completed, created_at, updated_at)
VALUES (:memberId, :activityDate, :allCompleted, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    all_completed = GREATEST(all_completed, VALUES(all_completed)),
    updated_at    = NOW(6)
```

**`GREATEST` 가 두 가지를 한꺼번에 지킨다.**

| | |
| --- | --- |
| 그룹·챌린지 인증이 `0` 을 써도 | 이미 선 `1` 을 **덮어 내리지 않는다** |
| 개인 루틴 완료가 `1` 을 쓰면 | 행이 이미 있어도 **올라간다** |

**한 번 `1` 이면 그날은 `1` 로 굳는다.** 그날 예정된 것을 다 한 순간이 있었다면 그건 사실이고, 저녁에 루틴을 하나 더 만들었다고 오늘이 소급해서 실패가 되면 **"루틴을 추가하면 그날을 잃는" 규칙**이 된다. 그건 루틴을 늘리지 말라는 뜻이 되어 앱이 권하는 행동과 반대다.

#### 이 표로 `CATEGORY_DAYS` 를 셀 수는 없다

**카테고리 칸이 없어서 하루에 무엇을 했는지가 남지 않는다.** 운동과 공부를 같은 날 해도 행은 하나이고, 그 행에는 종류가 없다. 여기에 카테고리를 더하면 하루에 행이 여러 개가 되어 "하루 1일" 을 지키던 유니크가 무너진다.

**`CATEGORY_DAYS` 는 인증 기록에서 직접 센다.** 개인은 `member_routine_verification → member_routine.category_id`, 그룹은 `group_routine_verification`, 챌린지는 `challenge.category` 다. 논리 키를 세 체계에 매핑하는 코드가 어차피 필요하므로(위 [condition_param] 참고), 집계도 같은 자리에서 한다.

**세는 것은 건수가 아니라 날짜다.**

```sql
COUNT(DISTINCT 인증의 KST 날짜)   -- 논리 카테고리 하나에 대해
```

`COUNT(*)` 로 세면 **하루에 같은 카테고리 루틴을 셋 하면 3일로 잡힌다.** 조건이 묻는 것은 "며칠 했는가" 이지 "몇 번 했는가" 가 아니다. 세 원천을 합칠 때도 **날짜 집합을 합집합으로 모은 뒤 세어야** 한다 — 원천별로 세어 더하면 같은 날이 겹쳐 두 번 들어간다.

**챌린지 재참여가 같은 날짜를 두 번 만든다.** 인증 행이 참여 회차(`participation_round`)마다 따로 생기기 때문이다. 날짜로 중복을 제거하면 이것도 함께 해결되므로 회차를 따로 거르지 않는다.

> 활동일 표는 **`ACTIVE_DAYS` 전용**이다. "며칠 했는가" 를 묻는 조건이 다 이 표 위에서 성립한다고 적었던 것은 틀렸다 — 카테고리를 묻는 순간 원천이 달라진다.

```sql
SELECT COUNT(*) FROM member_activity_day WHERE member_id = :memberId;
```

이 설계에서 따라오는 것들이다.

| | |
| --- | --- |
| 하루에 여러 개 완료해도 1일 | 유니크가 두 번째 INSERT 를 막는다 |
| 재화로 못 건너뛴다 | 이 표에 쓰는 경로를 루틴 완료 하나로만 두면 자동이다 |
| 동시성에 잠금이 필요 없다 | 루틴 둘을 동시에 완료해도 유니크가 하나를 튕겨 낸다. 그 예외를 삼키면 끝이다 |

#### 무엇이 "루틴 완료"인가 — 셋을 합산한다

```text
POST /api/routines/{id}/verifications                 개인 루틴
POST /api/groups/{gid}/routines/{id}/verifications    그룹 루틴
POST /api/challenges/.../verifications                챌린지 인증
```

**셋 다 활동일을 만든다.** 해금은 "앱을 꾸준히 썼는가"에 대한 보상이지 특정 기능을 밀어주는 장치가 아니다. 개인 루틴만 세면 **그룹·챌린지 위주로 쓰는 사람은 매일 인증해도 조건이 안 오른다.** 합산해도 빨라지지 않는다 — 어차피 하루 1일이다.

> **이 표는 캐릭터 전용이 아니다.** 전역 활동일은 업적·리포트처럼 "얼마나 꾸준히 썼는가" 를 묻는 곳이면 어디서나 쓴다. 캐릭터가 첫 소비자일 뿐이다.

### 해금은 한 번만 일어나야 한다

**판정이 여러 번 돈다.** 인증이 들어올 때마다 조건을 보고, 요청 둘이 동시에 들어오기도 한다. 그때 캐릭터가 두 번 열리거나 팝업이 두 번 뜨면 안 된다.

**유니크가 승자를 하나로 만든다.** `uk_member_character(member_id, character_id)` 로 두 번째 INSERT 가 튕긴다. **INSERT 에 성공한 요청만 팝업을 발행하고, 둘을 같은 트랜잭션에 둔다** — 나누면 해금만 되고 팝업이 없는 상태가 남는다.

제약 위반은 "졌다"는 뜻이므로 **조용히 끝낸다.** 오류로 올리지 않는다.

> 지급이 유니크로 승자를 가리고 그 트랜잭션 안에서 후속 처리를 하는 것과 같은 방식이다.

### `pending_popup` — 캐릭터 전용이 아니다

해금은 **반드시 보여줘야 하는 1회성 알림**이다. 소비자가 이미 둘이다(캐릭터, 업적 달성). 범용 모듈로 둔다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | `member.id` FK |
| popup_type | VARCHAR(30) | N | `CHARACTER_UNLOCKED` · `ACHIEVEMENT_ACHIEVED` … |
| title | VARCHAR(100) | N | **발행자가 채운다** |
| body | VARCHAR(255) | N | 발행자가 채운다 |
| image_key | VARCHAR(512) | Y | 발행자가 채운다 |
| reference_type | VARCHAR(30) | Y | 눌렀을 때 어디로 보낼지 |
| reference_id | BIGINT | Y | 그 대상의 id |
| dedup_key | VARCHAR(100) | N | 중복 발행 차단 |
| acked_at | DATETIME(6) | Y | `NULL` 이면 아직 안 보여줬다 |
| created_at | DATETIME(6) | N | 생성 시각 |

유니크: `uk_pending_popup_member_dedup` (`member_id`, `dedup_key`)

#### `dedup_key` 는 결정적이어야 한다

**요청 id 나 UUID 를 넣으면 유니크가 아무것도 막지 못한다.** 재시도할 때마다 다른 키가 되어 팝업이 쌓인다. 같은 사건이면 몇 번을 다시 계산해도 **같은 문자열이 나와야** 한다.

| `popup_type` | `dedup_key` |
| --- | --- |
| `CHARACTER_UNLOCKED` | `character-unlocked:{characterId}` |
| `ACHIEVEMENT_ACHIEVED` | `achievement-achieved:{achievementId}` |

`member_id` 가 유니크 키의 선두 컬럼이라 키 안에 회원을 다시 넣지 않는다. 사건이 회차·기간을 갖는 종류라면 그 값까지 키에 넣는다 — **"같은 사건"의 범위를 정하는 것이 이 키의 전부다.**

**모듈은 도메인을 몰라야 한다.** 표에 `character_id` 같은 참조만 두면 조회할 때 모듈이 캐릭터·업적을 전부 알아야 하고, 그러면 소비자가 늘 때마다 모듈을 고쳐야 해 모듈화한 의미가 없다. **발행하는 쪽이 보여줄 내용을 채워 넣고 모듈은 그대로 돌려준다.**

`title`·`body` 를 복사해 두면 마스터가 바뀌어도 옛 팝업은 옛 문구로 뜨지만, **팝업은 곧 뜨고 사라지므로 실질적으로 문제가 되지 않는다.** 결합을 끊는 값이 더 크다.

#### 푸시는 이 모듈이 보내지 않는다

**앱 밖으로 알리는 일은 알림 도메인이 한다.** FCM 은 이미 있고(`FcmDevice`·`NotificationDeliveryService`), 팝업이 그것을 직접 부르면 *"모듈은 도메인을 모른다"* 가 깨진다 — 알림 설정·기기 토큰·전송 실패 재시도를 팝업이 알게 된다.

**발행하는 쪽이 둘을 함께 부른다.**

```text
해금 판정 (한 트랜잭션)
├─ pending_popup 발행     앱을 켜면 반드시 뜬다
└─ notification 발행      알림함 + 푸시
```

둘은 성격이 다르다. **팝업은 "앱 안에서 반드시 한 번"이고 푸시는 "앱 밖에서 알린다"** — 푸시를 못 받아도(꺼 놨거나 토큰이 없거나) 팝업은 다음에 켤 때 그대로 뜬다. 그래서 푸시가 실패해도 해금 사실이 사라지지 않는다.

`NotificationType` 에 종류를 더하는 것은 알림 도메인의 일이고, 팝업 표는 그대로 둔다.

#### 표시는 최소 한 번이다 — 선점하지 않는다

조회는 `acked_at IS NULL` 인 행을 돌려주고 **아무것도 잠그지 않는다.** `ack` 는 그 행의 `acked_at` 을 채우며, **이미 채워져 있어도 성공으로 끝난다**(멱등).

그래서 **두 기기가 동시에 조회하면 양쪽에 뜬다.** 막지 않는다 — 이 팝업은 "반드시 보여야 하는 것" 이라 **덜 보이는 쪽이 더 나쁜 실패**다. 표시 권한을 미리 잡는 방식(claim token·lease)을 쓰면 앱이 표시 직전에 죽었을 때 만료될 때까지 **어느 기기에도 안 뜨는 구간**이 생기고, 그 시간을 얼마로 할지도 정할 근거가 없다.

**표시 도중 앱이 죽으면 `ack` 가 안 오므로 다음 조회에 다시 뜬다.** 이것도 의도한 동작이다.

#### 기존 `notification` 을 재사용하지 않는 이유

| | 알림함 (`notification`) | 팝업 (`pending_popup`) |
| --- | --- | --- |
| 성격 | 목록. 나중에 봐도 된다 | 즉시 1회. **반드시 봐야 한다** |
| 읽음 | 사용자가 열었을 때 | **앱이 띄운 직후 확인(ack)** |
| 푸시 | `push_status`·`push_attempts` 로 관리 | 해당 없음 |

### 자산은 사용자 업로드가 아니다

캐릭터·아이템 이미지는 운영이 올리는 마스터 자산이다. `MediaPurpose.AVATAR_ASSET` 을 더하되 **presigned PUT 도, 레이트 리밋 정책도 두지 않는다** — 발급 경로가 없으니 셀 것도 없다. `CHAT_EMOTICON` 과 같은 취급이다.

**DB 에는 절대 URL 이 아니라 key 를 넣는다.** 도메인이 바뀌면 행을 전부 고쳐야 하고, CloudFront 를 붙이는 순간 그 값이 전부 낡는다. 조회에서 `resolveViewUrl` 로 조립한다.

**`avatar_item.image_url` 도 함께 바꾼다.** 지금 절대 URL(`https://placehold.co/...`)이라 이 계약과 어긋나 있다. 컬럼을 `image_key` 로 바꾸고 시드 값을 key 로 교체한 뒤, 조회에서 `resolveViewUrl` 로 조립한다. **실사용 데이터가 아직 없어 지금이 제일 싸다** — 나중에는 이미 저장된 행을 옮기는 백필이 붙는다.

**자산을 교체할 때 같은 key 를 덮어쓰지 않는다.** 캐시가 옛 그림을 들고 있으면 사용자마다 다른 것을 본다. 새 key 로 올리고 마스터 행을 가리키게 바꾼다.

#### key 배치 — 이미 올라가 있다

```text
avatar/character/{code}/adult-v1.png    해금 뒤 성체
avatar/character/{code}/egg-v1.png      잠긴 상태
avatar/nest/level1-back-v1.png          level1-front-v1.png
avatar/nest/level2-back-v1.png          level2-front-v1.png
```

**꼬리의 `-v1` 이 교체 규칙을 강제한다.** 같은 이름으로 덮어쓸 자리가 없으므로 새 자산은 `-v2` 가 되고, 시드가 그것을 가리키도록 고치는 것이 곧 교체다.

`{code}` 는 `character.code` 그대로다. 캐릭터 열셋의 코드는 이렇다.

```text
ROUTI  NOA  MORI  KOKO  YUKI  MINT  SOLA
PADO   KKARU  DONGGEUL  PPIA  HORONG  DAMI
```

> **자산은 전부 400×400 이고 바닥 정렬이다.** 레이어를 겹칠 때 오프셋이 필요 없다는 뜻이고, 위 [겹쳐 그리려면 모든 장이 같은 캔버스여야 한다] 가 요구하는 규격을 이미 만족한다. **의상은 아직 이 규격이 아니다** — `viewBox` 가 아이템마다 달라 다시 받아야 한다.

### 정리하며 닫은 것

- **좋아요 조건은 좋아요 도메인을 건드리지 않는다.** 누적 대신 지금 눌러 둔 수를 세는 것으로 뜻을 바꿨다(위 [`LIKE_GIVEN`] 참고)
- **알 그림은 해금 뒤 쓰지 않는다.** 도감 같은 화면이 없다 — `egg_image_key` 는 목록의 잠금 표시 전용이다
- **복구권은 존재하지 않는 기능이다.** 기획 문구에 *"복구권을 사용한 날은 포함하지 않는다"* 가 있었으나 만들 계획이 없으므로 조건에서 뺐다. 나중에 복구권이 생긴다면 그때 이 조건들을 다시 본다

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

## 재화 충전·교환 테이블 — 설계안 (합의 필요)

> **아직 구현되지 않았다.** 결제는 **포트원(PortOne)** · **토스페이먼츠**로 간다.
> **환불은 이 단계에서 만들지 않는다** — 아래 [환불을 만들지 않는다] 에 그 결과를 적어 두었다.

화면의 탭이 둘이고 **성격이 완전히 다르다.**

| 탭 | 내는 것 | 받는 것 | 성격 |
| --- | --- | --- | --- |
| 파란보석 | **현금** (₩1,100 ~ ₩110,000) | `GEM` + 보너스 | **결제** |
| 주황보석 | `GEM` 3·5·10·20 개 | `TOPAZ` 100·200·500·1100 | **재화 교환** |

**교환은 결제와 무관하다.** 지갑의 차감·지급만 쓰므로 포트원 없이도 만들 수 있다.

### 기존 `payment`·`member_order` 를 쓰지 않는다

`payment` 는 `member_order` 에 묶여 있고, 그 표는 **실물 배송 주문**이다(배송지가 있다). 재화 충전에는 배송지도 주문 상세도 없다. **별도 표로 간다** — 아바타 아이템이 `product` 를 재사용하지 않은 것과 같은 이유다.

### 상품 마스터를 둘로 나눈다

한 표에 담으면 **`price_krw` 는 충전에만, `price_currency` 는 교환에만** 의미가 있어 어느 컬럼이 언제 유효한지 매번 따져야 한다. 그 문제를 피하려고 `product` 를 안 쓰기로 해 놓고 같은 표를 새로 만들 이유가 없다.

#### `charge_product` — 현금으로 사는 것

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키. 시드가 고정 id 로 넣는다 |
| reward_currency | VARCHAR(20) | N | 받는 재화. 지금은 `GEM` 만 |
| reward_amount | INT | N | **유상**으로 들어갈 수량 |
| bonus_amount | INT | N | **무상**으로 들어갈 보너스. 기본값 `0` |
| price_krw | INT | N | 결제 금액(원) |
| popular | TINYINT(1) | N | "인기" 뱃지. 기본값 `0` |
| sort_order | INT | N | 목록 순서. 기본값 `0` |
| active | TINYINT(1) | N | 판매 여부. 기본값 `1` |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- **`reward_amount` 와 `bonus_amount` 를 나눈다.** 팝업이 `550코인` 과 `+50 보너스` 를 화면에서 이미 구분한다. 합쳐 두면 **나중에 못 쪼갠다** — 환불 가능액(유상)과 소멸 대상(무상)을 가를 수 없다.
- **"인기" 뱃지는 여기서는 컬럼이다.** 아바타 아이템과 다르다 — 저기서는 판매량으로 계산할 수 있지만, 충전 묶음은 **운영이 밀고 싶은 것을 고르는 것**이라 집계로 나오지 않는다.

**제약으로 못 박는다.**

```sql
CHECK (reward_currency = 'GEM')                          -- 아래 설명
CHECK (reward_amount >= 0 AND bonus_amount >= 0)
CHECK (reward_amount + bonus_amount > 0)
CHECK (price_krw > 0)
```

**`reward_currency` 를 `GEM` 으로 못 박는 이유가 특히 중요하다.** 무료 재화(`TOPAZ`)를 파는 상품이 실수로 등록되면, **결제는 성공하는데 지급에서 거부된다** — `TOPAZ` 는 유상 잔액을 가질 수 없기 때문이다(위 [무료 재화에 유상 잔액을 만들 수 없다]). **돈은 나갔는데 재화가 안 들어오는** 상태가 되므로, 그런 상품이 만들어지는 것 자체를 막는다.

> 무료 재화를 현금으로 팔기로 정책이 바뀌면 이 제약과 `Currency.TOPAZ` 의 성질을 **함께** 봐야 한다. 한쪽만 풀면 위 상태가 그대로 재현된다.

#### `exchange_product` — 재화로 사는 것

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| from_currency | VARCHAR(20) | N | 내는 재화 |
| from_amount | INT | N | 내는 수량 |
| to_currency | VARCHAR(20) | N | 받는 재화 |
| to_amount | INT | N | 받는 수량 |
| sort_order | INT | N | 목록 순서 |
| active | TINYINT(1) | N | 판매 여부 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- **비율을 코드에 박지 않는다.** 묶음마다 다르고(100/3 · 200/5 · 500/10 · 1100/20 — 많이 살수록 유리하다) 운영이 바꾼다. `R__` 시드로 관리한다.
- **받는 재화는 무상으로 들어간다.** `TOPAZ` 는 무료 재화라 유상 잔액이 있을 수 없다 — 위 [무료 재화에 유상 잔액을 만들 수 없다] 를 볼 것.

```sql
CHECK (from_currency <> to_currency)      -- 같은 재화끼리 바꾸는 것은 뜻이 없다
CHECK (from_amount > 0 AND to_amount > 0)
```

> ⚠️ **"교환했으니 환불 책임이 끝난다" 고 단정하지 않는다.** 전자상거래법(제17조 제2항 제5호)은 디지털 콘텐츠의 **제공이 개시된 부분**에 한해 청약철회를 제한하는데, 그 제한은 **사업자가 철회 불가 사실을 명확히 고지했을 때** 성립한다. 고지가 없으면 제한 사유가 있어도 철회할 수 있다. 또 **가분적** 콘텐츠는 제공되지 않은 부분에 대해 철회가 가능하다.
>
> 그래서 교환의 환불 효과는 **조건부**다 — 고지·동의 절차를 갖추었는가, 받은 재화를 이미 썼는가에 따라 갈린다. **법무 검토 뒤 약관과 함께 정한다.**

### `charge_payment` — 결제 한 건

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| member_id | BIGINT | N | 결제 회원, `member.id` FK |
| charge_product_id | BIGINT | N | 상품, `charge_product.id` FK |
| payment_id | VARCHAR(64) | N | **서버가 결제 전에 만드는 식별자.** `UNIQUE`. 포트원 V2 의 `paymentId` 다 |
| tx_id | VARCHAR(64) | Y | **포트원이 만드는 거래 식별자.** `UNIQUE`. 결제 전에는 비어 있다 |
| status | VARCHAR(20) | N | `READY` · `PAID` · `FAILED`. 아래 [환불을 만들지 않는다] 참고 |
| expected_amount | INT | N | **결제 시작 시점에 서버가 기록한 금액** |
| reward_currency | VARCHAR(20) | N | **결제 시점** 지급 재화 (스냅샷) |
| reward_amount | INT | N | **결제 시점** 유상 지급 수량 (스냅샷) |
| bonus_amount | INT | N | **결제 시점** 무상 지급 수량 (스냅샷) |
| paid_amount | INT | Y | 실제 결제된 금액 |
| requested_at | DATETIME(6) | N | 결제 시작 시각 |
| paid_at / failed_at | DATETIME(6) | Y | 상태 전이 시각 |
| fail_reason | VARCHAR(255) | Y | 실패 사유 |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

#### 지급 내용을 결제 시점에 복사한다

`charge_product_id` 만 두고 지급 때 상품을 다시 읽으면, **결제를 시작한 뒤 운영이 상품을 고치는 순간 어긋난다.** 금액 검증은 `expected_amount` 로 하니 통과하는데 **지급 수량이나 재화가 달라진다** — 5,500원을 내고 550개를 받기로 했는데 300개가 들어오는 식이다.

그래서 `reward_currency` · `reward_amount` · `bonus_amount` 를 **결제 시작 시점에 복사하고, 지급은 이 값만 본다.** 아바타 아이템이 구매 시점 단가를 스냅샷으로 남긴 것과 같은 이유다.

#### 포트원 **V2** 를 쓴다 — 식별자가 둘이다

V1 과 V2 는 식별자 모델이 다르므로 섞어 쓰면 안 된다.

| | 서버가 만드는 것 | 포트원이 만드는 것 |
| --- | --- | --- |
| V1 | `merchant_uid` | `imp_uid` (결제 후 발급) |
| **V2** | **`paymentId`** | **`txId`** |

**V2 로 간다.** 서버가 결제 전에 `paymentId` 를 만드는 구조라 **사전 등록 설계와 그대로 맞는다.** 컬럼·요청·조회·웹훅이 전부 이 계약을 따른다 — `payment_id` 로 우리 행을 찾고, `tx_id` 는 포트원이 알려주는 거래를 가리킨다.

- **`payment_id` 에 유니크를 건다.** 같은 결제로 두 번 지급되면 그대로 손해다. **애플리케이션 분기로만 막으면 뚫린다** — 이 저장소가 이미 겪은 종류다.
- 소프트 삭제를 두지 않는다. **결제 기록은 지우지 않는다.**

### 결제는 사전 등록으로 시작한다

```text
① 결제 시작   POST /api/shop/charges
              서버가 charge_payment 를 READY 로 만들고 payment_id ·
              expected_amount · 지급 스냅샷을 기록한 뒤 내려준다

② 결제창      클라이언트가 포트원 SDK 로 결제한다 (paymentId 를 넘긴다)

③ 검증·지급   POST /api/shop/charges/{paymentId}/complete
              서버가 포트원 조회 API 로 실제 결제를 확인하고
              저장된 행과 대조한 뒤 지갑에 넣는다
```

**① 이 없으면 ③ 에서 대조할 기준이 없다.** 클라이언트가 보낸 금액을 믿으면 **100원 결제하고 11만원어치를 받는** 길이 열린다. 아바타 아이템에서 가격을 마스터가 갖게 한 것과 같은 이유다.

#### ③ 은 저장된 행과 인증 회원에 묶는다

**요청 본문의 값으로 판단하지 않는다.** `payment_id` 로 **인증된 회원의** `charge_payment` 를 찾고, 없으면 거절한다.

```text
그 회원의 charge_payment 인가        아니면 404
상태가 READY 인가                    PAID 면 이미 지급됨 (조용히 통과)
포트원 조회 결과의 상태가 결제 완료인가
포트원이 말하는 금액 == expected_amount   정확히 같아야 한다
포트원이 말하는 paymentId == 저장된 값
```

**회원 확인이 없으면 유효한 결제를 남의 충전에 쓸 수 있다.** 남의 `paymentId` 를 알아내 자기 계정으로 `complete` 를 부르면, 검증은 전부 통과하는데 재화는 **부른 사람에게** 들어간다.

지급은 저장된 **스냅샷**을 쓴다 — `reward_amount` 를 유상으로, `bonus_amount` 를 무상으로. 멱등 키는 `charge:{charge_payment.id}` 다.

### 웹훅이 없으면 결제만 되고 지급이 안 되는 경우가 남는다

③ 은 **클라이언트가 불러 준다.** 결제 직후 앱이 죽거나 네트워크가 끊기면 **돈은 나갔는데 재화가 없는** 상태가 된다.

**포트원 웹훅을 받아 같은 처리를 한다.** 웹훅 payload 는 믿지 않는다 — 거기 실린 식별자로 **조회 API 를 다시 부르고**, ③ 과 똑같이 검증한다.

#### "나중 것은 멱등으로 흘린다" 는 지금 지갑에서는 성립하지 않는다

`WalletCommandService.grant` 는 **지갑을 잠그기 전에** 멱등 여부를 본다. 웹훅과 완료 요청이 동시에 들어오면 **둘 다 "아직 처리 안 됨" 으로 판단**하고, 뒤늦게 유니크 제약(`member_id`·`currency`·`idempotency_key`)에 걸린 쪽이 **조용히 통과하는 게 아니라 실패**한다.

그래서 둘 중 하나가 필요하다.

- **지갑 잠금을 얻은 뒤 멱등 여부를 다시 본다**, 또는
- **유니크 충돌을 기존 거래 조회 결과로 바꿔** 되돌려준다

`charge_payment` 쪽도 같다. **`READY` → `PAID` 전이를 조건부 갱신**(`WHERE status = 'READY'`)으로 하고, **바꾼 행이 0 이면 남이 이미 처리한 것**으로 본다. 읽고 나서 쓰면 둘 다 통과한다.

**지급이 실패한 경우의 재시도 규칙도 정해야 한다** — 결제는 `PAID` 인데 지갑 지급만 실패하면, 웹훅이 다시 와야 복구된다.

> 이 항목은 **지갑(`#204`)의 성질**이다. 충전을 구현할 때 지갑 쪽도 함께 손봐야 한다.

### 환불을 만들지 않는다 — 그 결과를 적어 둔다

**이 단계에서는 환불·취소를 구현하지 않기로 했다.** 데모 기간이고 **토스페이먼츠 테스트 채널**로만 붙어 실제 금액이 오가지 않는다.

그래서 이렇게 된다.

- 서버가 **취소 API 를 부르지 않는다.** 환불은 포트원 콘솔에서 사람이 한다
- **취소 통지(웹훅)를 받아도 재화를 회수하지 않는다.** 즉 **취소된 결제의 재화가 잔액에 그대로 남는다**
- 그래서 `charge_payment` 에 `cancelled_at` 을 두지 않는다. **기록하지 않을 상태를 컬럼으로 만들지 않는다**

> ⚠️ **실제 결제를 받기 시작하면 이 결정을 반드시 다시 본다.** 그때 정할 것은 **법무 검토가 앞서야 하고**, 아래는 단정이 아니라 검토 항목이다.
>
> - **청약철회** — 전자상거래법(제17조 제2항 제5호)은 디지털 콘텐츠의 **제공이 개시된 부분**에 한해 철회를 제한한다. 다만 그 제한은 **철회 불가 사실을 명확히 고지했을 때** 성립하고, **가분적** 콘텐츠는 제공되지 않은 부분에 대해 철회가 가능하다. 고지·동의 절차를 어떻게 갖출지가 먼저다
> - **이미 써 버린 재화** — 환불하면 잔액이 음수가 된다(거절 / 음수 허용 / 아이템 회수). 위 고지 여부에 따라 거절이 가능한지가 갈린다
> - **미성년자 결제** — 민법 제5조상 법정대리인 동의 없는 법률행위는 취소할 수 있다. 다만 **묵시적 동의**나 **처분을 허락한 재산의 범위** 안이면 제한될 수 있어, 일률적으로 "거절할 수 없다" 고도 "취소해야 한다" 고도 단정할 수 없다

**회수 자체는 어렵지 않다.** 지갑에 차감이 이미 있고 리워드 회수(`#52`)가 같은 모양을 만들어 두었다 — 정해야 하는 것은 코드가 아니라 정책이다.

### 결제사·배포

- **PG 는 토스페이먼츠**로 간다. 포트원은 계약 전에도 테스트 채널로 붙을 수 있다.
- **포트원 가맹점 식별코드와 API 시크릿은 환경변수로 주입한다.** 진짜 비밀값이므로 **기본값 없이** 선언해 미주입 시 부팅이 실패하게 한다.

> ⚠️ **스토어 배포 예정이 없어서 성립하는 설계다.** 앱 마켓의 결제 정책은 **출시 국가·storefront·앱 유형·대체 결제 프로그램**에 따라 달라지므로, "외부 PG 는 무조건 막힌다" 처럼 단정하지 않는다. **출시 국가와 storefront 를 확정한 뒤** 외부 결제·대체 결제·인앱결제 중 어느 경로가 적용되는지, 수수료와 **환불 주체가 누구인지**를 다시 판단해야 한다. 그 결과에 따라 결제 모델이 통째로 바뀔 수 있다.

## 인증(verification) 도메인 — 설계안 (합의 필요)

> **1·2·3단계가 모두 구현됐다.** 1단계(PR #111)와 3단계(PR #107)가 먼저 들어갔고, 2단계는
> 이 문서를 고치는 PR이 끝냈다. 세 대상의 인증이 `verification` 한 패키지에 모였다.
> 남은 것은 **key 범위 식별자**다.
>
> **사진을 어떻게 보여주는지는 대상마다 다르다.** 서명된 한시적 주소는 **비공개인 그룹·개인
> 루틴 인증**에만 해당한다. 챌린지 인증은 `challenge-verifications/` prefix가 공개라
> **조립된 공개 URL**을 그대로 내려준다 — 아래 [접근 범위 — 셋이 다르다](#접근-범위--셋이-다르다) 표와 같다.
> 이 차이를 뭉뚱그리면 "인증 사진은 서명해서 준다"로 읽혀, 챌린지 쪽에 서명 로직을
> 넣으려다 이미 공개된 것을 뒤늦게 알게 된다.
>
> 2단계에서 **에러·성공 코드 문자열은 `CHALLENGE*` 그대로 뒀다.** 클래스는 옮겼지만 그 문자열은
> 클라이언트가 분기에 쓰는 값이라, 맞춰 바꾸면 서버만 정리되고 앱이 깨진다. 그래서 패키지와
> 코드 문자열이 어긋나 보이는데, 의도한 것이다.
>
> 기획 확인이 필요했던 7가지는 아래 [확정된 정책]에 정리했다. 남은 미결은 탈퇴·삭제 처리 하나다.

인증은 원래 챌린지에만 있었다. 그룹 루틴·개인 루틴에도 사진 인증이 필요하고, **사진을 올리는 방식은 셋이 같지만 저장 후 처리가 다르다.** 그래서 인증을 별도 도메인으로 빼고 대상별 차이를 그 위에 얹었다.

### 셋이 서로 다른 모델이다

`날짜별 행`이 설계를 가르는 축이다. **그룹은 이미 날짜별 행이 있고 개인은 없다** — 이 차이 때문에 그룹은 인증이 완료에 붙는 증빙이고, 개인은 인증 행이 곧 완료다.

| | 완료를 어떻게 표현하나 | 사진 | 날짜별 행 | 조회 |
| --- | --- | --- | --- | --- |
| 챌린지 | `challenge_verification` 행 생성 | 필수 | 인증 행이 곧 그것 | 공개 피드 |
| 그룹 루틴 | `group_routine_assignment.status` 전이 | **필수** | **스케줄러가 매일 만든다** | 방 멤버만(presigned GET) |
| 개인 루틴 | `member_routine_verification` 행 생성 | **필수** | **없다.** `member_routine_schedule`은 요일만 정의 | **없음** |

> 사진 열은 설계 시점과 달라졌다. 원래 그룹 루틴에는 사진이 없었고 개인 루틴에는 완료 기록 자체가 없었는데, 3단계에서 셋 다 **사진 필수**가 됐다(확정된 정책 1·4번).
>
> 조회 열이 비대칭인 것도 의도다. **개인 인증만 조회 경로가 없다** — 그 사진을 보여 주는 화면이 없다는 기획 판단이다. 그래서 사진은 필수인데 읽는 쪽이 없다. 아래 [접근 범위] 절의 경고를 볼 것.

### 테이블은 셋으로 나눈다

하나로 합쳐 `target_type + target_id` 다형 참조를 쓰면 **FK 무결성을 잃는다.** 이 스키마는 전부 FK로 잡고 있어 그 원칙을 여기서만 깨는 건 되돌리기 어렵다. 유니크 키도 서로 다르다.

| 테이블 | 유니크 키 | 근거 |
| --- | --- | --- |
| `challenge_verification` (기존) | `(member_challenge_id, participation_round, period_start_date)` | 회차마다 다시 셀 수 있어야 하고, 주기 1회를 DB가 보장해야 한다 |
| `group_routine_verification` | `(group_routine_assignment_id)` | **할당 1건에 인증 1건.** 할당이 이미 `(루틴, 회원, 날짜)`로 유니크하므로 그 위에 얹으면 된다 |
| `member_routine_verification` | `(member_routine_id, verified_date)` | 날짜별 행이 없으므로 **인증 자체가 완료 기록**이다 |

두 테이블은 `V9__routine_verification.sql` 로 들어갔다. `member_routine_verification` 에는
`(verified_date, member_routine_id)` 인덱스를 하나 더 뒀다 — 루틴 목록에 "오늘 완료"를 붙일 때
루틴마다 묻지 않고 그날치를 한 번에 가져오는 경로다.

`group_routine_verification`이 할당을 참조하는 것이 이 설계의 핵심이다. 그룹은 "완료 여부"를 이미 `status`로 들고 있으므로, 인증 행은 **그 완료에 붙는 증빙**이지 완료 그 자체가 아니다. 반면 개인 루틴은 인증 행이 곧 완료다.

### 그룹 루틴 인증 좋아요

`group_routine_verification_like`는 그룹 루틴 인증 게시물에 대한 회원별 좋아요 이력이다. 좋아요 수는 별도 캐시 컬럼 없이 이 테이블의 현재 행 수로 집계한다. 인증 작성자나 좋아요 작성자의 그룹 회원 상태가 이후 변경되어도 행은 보존한다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | N | 기본 키 |
| group_routine_verification_id | BIGINT | N | 대상 `group_routine_verification.id` FK |
| member_id | BIGINT | N | 좋아요를 누른 `member.id` FK |
| created_at / updated_at | DATETIME(6) | N / N | 생성·수정 시각 |

- UNIQUE: `(group_routine_verification_id, member_id)` — 동일 회원의 중복 좋아요를 DB에서 차단한다.

### 접근 범위 — 셋이 다르다

**볼 수 있는 사람과 서빙 방식은 구현돼 있고, 경로의 범위 식별자는 아직 목표다.** 표에서 두 열을 갈라 둔 이유가 이것이다.

| 인증 | 볼 수 있는 사람 | 지금 발급되는 prefix | 목표 prefix | 서빙 |
| --- | --- | --- | --- | --- |
| 챌린지 | 전체 공개 | `challenge-verifications/` | (같음) | 공개 버킷 정책 |
| 그룹 루틴 | 그 방 멤버 전원 | `group-routine-verifications/` | `group-routine-verifications/{groupId}/` | **presigned GET** ✅ |
| 개인 루틴 | 본인만 | `member-routine-verifications/` | `member-routine-verifications/{memberId}/` | **조회 경로 없음** ⚠️ |

범위 식별자를 경로에 넣으려는 근거는 아래 [범위 식별자는 "누가 볼 수 있는가"의 단위다](#범위-식별자는-누가-볼-수-있는가의-단위다) 절과 같다. **공개 용도에는 식별자를 넣지 않고 비공개 용도에만 넣는다** — 비공개는 일괄 정리에 그 값이 필요하다.

> ⚠️ **"비공개라 경로가 노출되지 않는다"는 말은 틀렸다.** presigned GET URL 에는 **오브젝트 key 가 그대로 들어간다.** 서명은 *누가 언제까지* 열 수 있는지를 정할 뿐, key 를 가리지 않는다. 즉 식별자를 넣으면 **URL 을 받은 사람에게는 보인다.**
>
> 공개 prefix 와 다른 것은 **URL 을 누가 얻는가**다. 공개는 주소만 알면 아무나, 비공개는 접근 판정을 통과한 사람만 받는다. "안 보인다"가 아니라 "보는 사람이 제한된다"이고, 그 사람이 URL 을 흘리면 식별자도 함께 흘러간다.
>
> 그래서 식별자는 **유출돼도 감당할 값**이어야 한다. `groupId`·`memberId` 는 그 URL 을 받은 사람이 이미 그 방 멤버이거나 본인이라 새로 알게 되는 정보가 없다. 반면 공개 prefix 에 넣으면 아무나 "412번 회원이 3번 챌린지를 했다"를 읽을 수 있어 성격이 다르다.

아직 식별자를 넣지 않은 이유는 바로 아래 경고를 볼 것.

**presigned GET 이 구현됐다.** 조회 API 가 응답에 서명된 주소를 실어 내려준다.

```text
GET /api/groups/{gid}/routines/{rid}/verifications             그 방의 활성 멤버만
```

> ⚠️ **개인 루틴 인증에는 조회 API 를 두지 않았다.** 그 사진을 보여 주는 화면이 없다는 기획 판단이다.
>
> 그 결과 **개인 인증 사진은 저장만 되고 읽는 경로가 없다.** 사진은 여전히 필수인데(`image_url NOT NULL`) 읽는 쪽이 없으므로, 그 사진은 S3 용량만 차지한다. 완료 여부는 인증 행의 존재로 판정하므로 기능은 돌아간다 — 즉 **사진이 없어도 지금 화면은 똑같이 동작한다.**
>
> 둘 중 하나로 정리해야 한다. **개인 인증 사진을 보여 주는 화면을 만들거나, 사진을 선택으로 낮추거나.** 지금 상태를 오래 두면 아무도 안 보는 파일이 계속 쌓인다.

세 가지를 정해 두었다.

- **접근 판정이 서명보다 먼저다.** 서명을 붙이는 순간 그 URL 을 가진 사람은 누구나 볼 수 있으므로, 자격 판정을 잘못하면 그것이 곧 유출이다. 방 멤버 검증은 그룹 도메인의 기존 `GroupValidationService` 를 재사용한다 — 접근 규칙이 두 벌이 되지 않게.
- **서명 유효 시간은 업로드용과 분리한다**(`aws.s3.view-url-expiration`, 기본 15분). 업로드 URL 은 발급 직후 PUT 한 번에 쓰이고 끝나지만, 조회 URL 은 목록 응답에 실려 화면에 머무는 동안 계속 쓰인다. 업로드와 같은 5분을 주면 잠깐 다른 앱을 보고 돌아왔을 때 사진이 전부 깨진다.
- **공개·비공개 갈림은 `MediaPurpose` 가 소유한다**(`publicRead`). 호출부가 "이건 공개라 저 메서드"를 기억해야 하면 새 용도가 늘 때 한 곳만 안 고쳐져 비공개 사진이 서명 없는 URL 로 나간다. **이 값은 버킷 정책과 짝이므로 바꿀 때 정책을 함께 확인한다.**

> ⚠️ **key 범위 식별자는 아직 빠져 있다.** 실제로는 `group-routine-verifications/{yyyy}/{MM}/{dd}/{UUID}.jpg` 형태로 만들어진다. 식별자를 넣으려면 presigned URL 발급 요청이 `groupId` 를 함께 받아야 하는데(`memberId` 는 서버가 알지만 `groupId` 는 아니다) 그건 **클라이언트 변경이 딸린다.**
>
> 서빙과 함께 맞추려 했으나 분리했다. **접근 판정이 식별자에 기대지 않기 때문이다** — 판정은 인증 행을 찾아 소유자·방 멤버를 확인하는 방식이고, 경로에 식별자가 있어도 클라이언트가 보낸 값이라 어차피 DB 와 대조해야 한다. 즉 지금 넣어도 판정이 더 안전해지지 않는다.
>
> 식별자가 실제로 값어치를 갖는 것은 **그룹 해체 시 prefix 단위 정리**와 다층 방어다. 그 둘 다 급하지 않고, 반대로 발급 요청 스펙을 바꾸는 것은 클라이언트와 맞물려 있다. **별도 작업으로 뺀다** — 그때 정리 배치의 prefix 열거(`{prefix}/{yyyy}/{MM}/{dd}/`)가 식별자 한 단계를 더 타야 한다는 점도 함께 처리한다.

### AI 심사는 챌린지만

기획 확정 사항이다. 공통 저장 파이프라인에서 대상별로 켜고 끈다. 그룹·개인 인증은 사진 형식·바이트 검증까지만 거친다.

**유해성 심사도 붙이지 않는다.** 챌린지의 AI 심사는 미션 부합과 공개 가능 여부 둘을 보는데, 그룹·개인 인증에는 둘 다 적용하지 않는다. 그룹은 방 멤버끼리만, 개인은 본인만 보는 사적 공간이라는 판단이다.

그 결과 **그룹 방에는 부적절한 사진에 대한 대응 수단이 없다** — AI 심사도 신고도 없다. 초대코드로 모르는 사람이 들어올 수 있다는 점은 남겨 둔다. 문제가 되면 신고를 먼저 붙이는 편이 AI 심사보다 싸다.

### 심사가 장애로 답을 못 주면 보류한다

> **구현 완료.** 아래는 합의된 규칙이고, 코드가 이대로 따른다.
> 설정은 `ai.review.pending.*` — 끄면 보류 없이 예전처럼 바로 통과시킨다(탈출구).

**예전에는 심사기가 답을 못 주면 그냥 통과시켰다.** 이 fail-open 은 근거가 있는 선택이었다 — *외부 API 하나가 인증 기능 전체를 멈추게 두지 않는다*. 문제는 **선택지가 "전부 통과"와 "전부 차단"뿐**이었다는 것이다.

**보류는 그 사이다.** fail-open 을 없애는 것이 아니라 **즉시 통과를 유예해 심사 기회를 준다.** 유예가 끝나면 결국 통과시킨다 — 사용자는 잘못한 것이 없기 때문이다.

#### 보류는 "다시 하면 달라질 수 있는 실패"에만 붙인다

`decided = false` 를 전부 보류로 보면 안 된다. **다시 심사해도 결과가 같을 실패가 섞여 있다.**

| 결과 | 처리 | 왜 |
| --- | --- | --- |
| `APPROVED` | 승격·저장 | |
| `REJECTED` | 422, 대기본 삭제 | 지금과 같다 |
| `TRANSIENT_FAILURE` — 심사기 장애·타임아웃, S3 일시 오류 | **보류** | 복구되면 판정이 달라진다 |
| `DISABLED` — `ai.review.enabled = false` | **통과** | 일부러 끈 것이다 |
| `NOT_APPLICABLE` — 챌린지 미존재, 사진이 심사 상한 초과 | **통과** | 다시 해도 같다 |

**결과 계약을 먼저 바꿔야 한다.** 지금은 이 다섯이 `decided`(boolean) 하나로 뭉개진다.

- `AnthropicVerificationReviewClient#review` 가 모든 `RuntimeException` 을 `undecided()` 로 합친다 → **장애와 "판정 불가" 가 구분되지 않는다**
- `MediaService#loadForReview` 의 `Optional.empty()` 도 **S3 일시 오류**와 **사진이 상한을 넘음**을 함께 뜻한다 → 앞은 보류, 뒤는 통과여야 하는데 같은 값이다

**킬 스위치를 가르는 것이 특히 중요하다.** 모델 오작동 시 쓰라고 만든 탈출구가 보류로 빠지면 **인증을 막는 스위치**가 된다.

#### 보류에 상한을 둔다 — 그래야 수명 주기와 부딪히지 않는다

**대기 prefix 의 수명 주기(1일)와 정면으로 충돌한다.** 1일 만료의 근거는 *"심사는 업로드 직후 끝나므로 하루를 넘긴 대기본은 버려진 것"* 이었는데, 보류가 생기면 그 전제가 깨진다. **장애가 하루를 넘기면 보류 중인 사진이 지워져 재심사할 대상이 사라진다.**

별도 prefix 를 새로 만들거나 태그로 예외를 두는 방법도 있지만, **보류에 상한을 두면 문제 자체가 없어진다.**

```text
보류 상한        24시간      ← pending_since 부터
수명 주기         3일         ← 오브젝트 업로드 시각부터
```

**두 시계의 기준이 다르다.** S3 수명 주기는 `pending_since` 가 아니라 **오브젝트가 만들어진 시각**부터 센다. 업로드와 보류 시작 사이에는 바이트 검증·심사 시도가 들어가지만 초 단위라, 실질적으로 `업로드 + 24시간` 이 상한이다.

3일로 잡는 것은 그 위에 **여유를 얹은 값**이다 — 재심사 스케줄러가 상한 직후에 바로 돌지 않을 수 있고, 확정 처리(승격 또는 반려)도 한 번 더 실패할 수 있다. **상한(1일)과 수명 주기(3일) 사이의 이틀이 그 여유다.**

상한에 닿으면 아래 [유예가 끝나면 통과시킨다] 대로 처리하므로, **3일을 넘겨 살아 있어야 하는 대기본은 존재하지 않는다.** 수명 주기는 지금처럼 나이 기반 한 줄이면 된다.

> ⚠️ **수명 주기 규칙은 이 값이 정해진 뒤에 건다.** 먼저 1일로 걸어 두고 나중에 늘리면 그 사이 보류 건이 지워진다.

#### 보류 중에도 인증 행은 저장한다

사용자는 이미 사진을 올렸다. 저장하지 않으면 그 수고가 사라지고, **재심사 대상을 DB 밖에서 관리해야 한다.**

`challenge_verification` 에 컬럼을 더한다.

| 컬럼 | 뜻 |
| --- | --- |
| `review_status` | `PENDING` / `APPROVED` |
| `review_attempts` | 재심사 시도 횟수 |
| `pending_since` | 보류가 시작된 시각. 상한 판정의 기준 |

**기존 행은 마이그레이션에서 `APPROVED` 로 채운다.** 지금까지 저장된 인증은 모두 심사를 지났거나 심사가 없던 시절의 것이므로 보류가 아니다.

##### PENDING 행이 어느 조회에 들어가는지 전부 정한다

**이 표를 빠뜨리면 보류 건이 남의 화면에 샌다.** 지금 `challenge_verification` 을 읽는 곳은 넷이고, 기본값은 "제외" 다.

| 읽는 곳 | PENDING 포함 | 왜 |
| --- | --- | --- |
| 공개 피드 | ❌ | 승격되지 않아 공개 대상이 아니다 |
| 인증 게시글 수(상세 통계) | ❌ | 피드와 같은 수를 보여야 한다. 세는 것과 보이는 것이 다르면 화면이 어긋난다 |
| 챌린지별 내 인증 목록 | ✅ **본인 것만** | 방금 올린 사진이 사라지면 안 된다. 상태를 함께 내려 "심사 중" 을 그린다 |
| 마이페이지 통합 내 인증 | ✅ **본인 것만** | 챌린지별 목록과 같은 기준이다. 한쪽만 다르면 <b>같은 사진이 한 화면에서는 "심사 중", 다른 화면에서는 그냥 인증</b>으로 보인다 |
| 현재 주기 인증 여부(버튼) | ✅ | 인증은 실제로 했다. 아래 [스트릭은 보류 시점에 올린다] 와 같은 기준이다 |

> **"내 인증 목록" 이 두 곳이다.** `GET /api/challenges/{id}/verifications/me` 와
> `GET /api/members/me/verifications` — 뒤쪽은 개인·그룹·챌린지 인증을 날짜별로 모아 주는
> 마이페이지 화면이고, 그 안에 챌린지 인증이 들어 있다. **둘 다 같은 규칙을 받아야 한다.**
>
> 뒤쪽은 지금 `MediaService#resolveViewUrl` 로 주소를 만드는데, **보류 건에 그대로 쓰면 안 된다** —
> 아래 [보류 중 사진은 본인에게만 보인다] 참고. 챌린지 인증은 `publicRead = true` 라 공개 주소를
> 조립해 돌려주고, 보류 사진은 그 주소로 열면 403 이다.

**보류만 모아 보는 경로는 따로 만들지 않는다.** 위 두 목록에 `status` 필터를 붙인다. 새 경로를 파면 같은 데이터를 두 경로가 각자 조회하게 되고, **제외 규칙이 갈라질 자리가 하나 더 생긴다.** 필터를 안 주면 지금과 같이 전부 내려간다 — 기본값이 바뀌면 파라미터를 안 보내는 기존 클라이언트의 화면이 조용히 달라진다.

**"세는 것과 보이는 것"이 갈리는 자리가 둘이다.** 게시글 수는 피드를 따라가고(둘 다 노출), 완료자 수는 스트릭을 따라간다(둘 다 수행 기록). 신고 숨김(`hidden_at`)이 노출 경로에만 붙고 스트릭·완료자 수에는 안 붙는 것과 같은 갈림이다.

**`REJECTED` 상태는 두지 않는다.** 지금도 반려는 행을 만들지 않고 422 로 끝나며, 사용자는 다시 찍어 올리면 된다. 반려 행을 남기면 유니크 제약 `(참여, 회차, 날짜)` 이 그 재시도를 막는다. **보류가 반려로 확정되면 오늘 반려와 같은 결과로 만든다** — 행을 지우고 사진도 지운다.

#### 스트릭은 보류 시점에 올린다

가장 갈리는 지점이라 근거를 적어 둔다.

- **안 올리면** 장애 동안 사용자는 "인증했는데 0일" 을 본다. 남의 서비스 장애로 내 기록이 끊기는 것은 납득하기 어렵다
- **올렸다가 반려되면** 되돌려야 한다. 다만 스트릭은 조회 시점 판정이라 되돌리는 쪽이 오히려 다루기 쉽고, **반려는 드물다**

그래서 **올린다.**

##### 반려가 확정되면 빼는 것이 아니라 다시 센다

`current_streak` 을 1 줄이거나 `last_verified_date` 를 전날로 되돌리는 방식은 **틀린 값을 남긴다.** 보류가 스트릭에 반영된 뒤 다음 날 승인 인증이 붙었다면, 그 뒤에 반려가 확정될 때 단순 감산은 이후 인증까지 지운 셈이 된다.

**그 회차의 유효한 인증 행으로 다시 계산한다.**

```text
유효 = review_status = APPROVED           (보류·반려 제외)
기준 = verified_date (KST)
결과 = last_verified_date · current_streak 를 그 목록에서 새로 구한다
```

**잠금은 저장 경로와 같은 것을 쓴다** — `findByMemberChallengeIdAndChallengeIdForUpdate`. 재계산과 인증 저장이 같은 행을 건드리므로, 다른 잠금을 쓰면 둘이 서로를 덮어쓴다. 한 트랜잭션 안에서 잠그고 → 다시 세고 → 쓴다.

> 이 보정은 **인증 글 삭제(별도 이슈)와 같은 문제**다. 삭제도 "인증 한 건이 사라졌을 때 스트릭을 어떻게 되돌리나" 를 풀어야 한다. **두 이슈가 같은 로직을 공유하므로 먼저 정해지는 쪽을 따른다.**

**주기 1회는 보류 건도 그 구간 인증으로 친다.** 유니크 제약이 자연히 그렇게 만든다. 안 그러면 장애 동안 같은 구간에 여러 건이 쌓인다.

#### 보류 중 사진은 본인에게만 보인다

승격 전이라 공개 URL 이 없다. 그렇다고 아무것도 못 보여주면 방금 올린 사진이 화면에서 사라진다.

**`#103` 1단계에서 만든 presigned GET 을 쓴다.** 다만 **`resolveViewUrl` 을 그대로 부르면 안 된다.**

그 메서드는 `MediaPurpose` 만 보고 갈리는데, 챌린지 인증은 `publicRead = true` 라 **공개 주소를 조립해 돌려준다.** 보류 중인 사진은 대기 prefix 에 있어 그 주소로 열면 403 이다. **보류 건은 서명 발급을 명시적으로 부른다.**

**권한은 미디어 계층이 못 막는다.** 서명 발급은 key 만 알아서 소유자를 모른다 — 이 갈림은 이미 `resolveViewUrl` 주석에 적혀 있다. 따라서 **조회 서비스가 "본인 것" 을 보장한 뒤에만 서명을 요청한다.**

- 내 인증 목록은 이미 `memberId` 로 좁혀 조회한다 — 그 경로에서만 보류 건에 서명을 붙인다
- 피드는 보류 건을 아예 담지 않으므로 서명을 만들 일이 없다
- **본인 확인 없이 보류 key 로 서명을 발급하는 경로를 만들지 않는다.** 만들면 그 순간 대기 prefix 가 공개된 것과 같아진다

- **피드에는 안 보인다.** 승격되지 않았으므로 공개 대상이 아니다
- **내 인증 목록에는 보인다.** 상태를 함께 내려 "심사 중" 을 그릴 수 있게 한다

#### 유예가 끝나면 통과시킨다

##### 시도 횟수와 간격

| 항목 | 값 | 뜻 |
| --- | --- | --- |
| 세는 대상 | **재심사만** | 최초 심사는 포함하지 않는다. `review_attempts` 는 보류로 들어온 뒤의 시도 수다 |
| 증가 시점 | **시도 직후** | 성공·실패와 무관하게 올린다. 실패할 때만 올리면 계속 죽는 호출이 상한에 안 닿는다 |
| 구분 | **원인을 나누지 않는다** | AI 오류든 S3 오류든 한 카운터다. 나누면 둘이 번갈아 실패할 때 어느 쪽도 상한에 안 닿는다 |
| 간격 | **10분** | 스케줄러 주기와 같다 |
| 상한 | **24시간 또는 12회 중 먼저 닿는 쪽** | 둘 다 두는 것은 스케줄러가 멈춰 있던 구간을 시간이 받아 주기 때문이다 |
| 동시 실행 | **금지** | 재심사 스케줄러는 한 번에 하나만 돈다. 겹치면 같은 건을 두 번 심사해 카운터가 두 배로 오른다 |

상한에 닿으면 **승격하고 `APPROVED` 로 확정한다.**

- **반려하지 않는다.** 사용자가 잘못한 것이 없는데 남의 장애로 반려하는 것은 부당하다
- **무기한 보류하지 않는다.** 사진이 영영 안 보이는 것도 같은 이유로 부당하다

즉 **결과는 지금과 같고 시점만 늦춰진다.** 그 사이 심사기가 살아나면 제대로 판정할 기회를 얻는다.

**심사 없이 통과한 사실은 반드시 로그에 남긴다.** 지금도 그렇게 하고 있고, 보류를 거친 경우에는 **보류 기간과 시도 횟수를 함께** 남긴다.

#### 보류 건수는 눈에 보여야 한다

**쌓이는 것을 아무도 모르는 상태가 가장 나쁘다.** 심사기가 오래 죽어 있으면 보류가 계속 늘고, 상한이 지나면 한꺼번에 무심사 통과가 된다.

최소한 **주기적으로 보류 건수를 로그에 남긴다.** 0 이면 남기지 않는다 — 평소에 조용해야 이상할 때 눈에 띈다.

### 인증 게시글 삭제

> **구현 완료.** 아래는 합의된 규칙이고 코드가 이대로 따른다.

인증 글을 지우는 경로가 없다. 메모 수정을 만들 때 *"삭제는 별도"* 로 미뤄 뒀고, 그때 미룬 이유가 지금도 그대로다.

#### 먼저 정할 것은 하나다 — 삭제가 무엇을 뜻하는가

나머지 결정이 전부 여기서 갈린다.

| | **A. 글을 내린다** | **B. 인증을 취소한다** |
| --- | --- | --- |
| 뜻 | 사진·글은 안 보이게 하되 **인증한 사실은 남는다** | 그날 인증이 **없던 일**이 된다 |
| 스트릭 | 그대로 | 되돌려야 한다 |
| 주기 1회 | 유지 — 다만 **지우면 다시 열린다**(아래 참고) | 다시 할 수 있다 |
| 되돌리기 로직 | **필요 없다** | `last_verified_date` · `current_streak` 보정 필요 |
| 사용자 기대와 | "왜 스트릭이 남지?" 가 나올 수 있다 | "지우면 다시 하면 되네" 가 된다 |

> ⚠️ **이 표는 도입 당시의 판단이고, 지금은 한 칸이 바뀌었다.** A 를 고르면서 "지워도 그 구간은 다시 못 연다" 로 두었는데, 그 뒤 **지우면 다시 열되 리워드를 회수하는 쪽**으로 정했다. B 가 걱정하던 "지우면 다시 하면 되네" 를 막는 역할이 저장 경로에서 재화 회수로 넘어간 것이다. 나머지 칸(스트릭을 그대로 둔다, 되돌리기 로직이 없다)은 그대로다.

**A 를 권고한다.** 이유는 셋이다.

1. **B 는 하루 1회를 뚫는다.** `인증 → 삭제 → 재인증` 을 반복할 수 있다. 재참여로 우회하던 것을 막아 놓고(아래 [주기 1회는 회차를 넘는다]) 삭제로 열어 주면 앞뒤가 안 맞는다
2. **B 의 되돌리기는 생각보다 어렵다.** 스트릭은 조회 시점 판정이라 이전 값을 복원하려면 앞선 인증들을 되짚어야 한다
3. **A 는 이미 있는 경로를 그대로 쓴다.** 당일 재인증이 덮어쓰기로 동작하므로, 지운 뒤 다시 올리면 그 행이 되살아난다 — "그날의 인증은 한 건" 이라는 성질이 유지된다

#### 그래서 소프트 삭제다 — 기존 근거는 지금 성립하지 않는다

위 `challenge_verification` 절에는 원래 **"이 테이블은 소프트 삭제하지 않는다"** 고 적혀 있었다. 근거는 이랬다.

> `deleted_at` 을 두면 소프트 삭제된 행도 유니크 제약에 그대로 남는다. (…) 같은 날 다시 인증하려 하면 삭제된 행 때문에 실패한다.

**그 근거는 "재인증 = INSERT" 를 전제한다.** 실제 구현은 그렇지 않다 — 당일 재인증은 기존 행을 찾아 `image_url`·`content`·`verified_at` 을 **UPDATE** 한다(`ChallengeVerification#reverify`). 소프트 삭제된 행도 그 조회에 그대로 걸리므로, 덮어쓰면서 `deleted_at` 을 지우면 된다. **유니크 제약과 부딪히지 않는다** — 다만 그 조회가 지운 행을 계속 찾아야 한다는 전제가 붙는다(아래 [지운 행이 어느 조회에 들어가는지 전부 정한다](#지운-행이-어느-조회에-들어가는지-전부-정한다)).

> 원래 근거가 틀렸다는 뜻은 아니다. `deleted_at` 을 둘 이유가 그때는 없었고, 두면 잃는 것만 보였다. **삭제 기능이 생기면서 얻는 쪽이 생겼다.**
>
> 위 절의 표와 설명은 실제 구현에 맞춰 갱신했다. 이 항목은 **왜 결정이 뒤집혔는지**를 남겨 둔 기록이다.

#### 지운 행이 어느 조회에 들어가는지 전부 정한다

**"조회에서 빠진다" 로 뭉뚱그리면 안 된다.** 이 테이블을 읽는 경로는 여섯이고, **그중 하나는 반드시 포함해야 한다.**

| 읽는 곳 | 지운 행 포함 | 왜 |
| --- | --- | --- |
| 공개 피드 | ❌ | 내려간 글이다 |
| 내 인증 목록 | ❌ | 본인이 내린 글이다 |
| 인증 게시글 수 | ❌ | 피드와 같은 수를 보여야 한다 |
| 현재 주기 인증 여부(버튼) | ❌ | **지우면 그 구간이 다시 열리므로 버튼도 열려야 한다.** 서버는 받아 주는데 버튼이 잠긴 채면 사용자가 거기 닿을 수 없다 |
| 보류 재심사 큐 · 보류 건수 집계 | ❌ | 아무도 볼 수 없는 행을 24시간 다시 심사하는 것은 낭비다. 더 나쁜 경우도 있다 — 삭제 때 대기 사진 지우기가 실패했다면 재심사가 통과시켜 **지운 사진을 공개 prefix 로 승격**한다 |
| **저장 경로의 "오늘 인증 찾기"** | ✅ **반드시** | 아래 참고 |

> ⚠️ **`findByMemberChallengeIdAndVerifiedDate` 에 `deleted_at IS NULL` 을 붙이면 안 된다.**
>
> 붙이면 지운 뒤 다시 인증할 때 그 행을 못 찾아 **새로 INSERT 하고, 유니크 제약에 걸린다.** 원래 이 문서가 소프트 삭제를 배제하며 걱정하던 바로 그 오류다 — **덮어쓰기 경로가 지운 행까지 찾아야만** 그 걱정이 실제로 사라진다.
>
> 소프트 삭제를 도입할 때 각 쿼리에 조건을 기계적으로 붙이기 쉬운데, **이 하나는 예외다.**

**버튼은 "완료" 인데 글이 없는 상태가 생긴다.** 인증한 사실은 남기고 글만 내렸으므로 의도한 결과다. 클라이언트가 그 조합을 그릴 수 있어야 한다 — 지운 사람은 자기가 지운 것을 안다.

**기존 행은 마이그레이션에서 `deleted_at = NULL` 이다.** 지금까지 저장된 인증은 아무도 지우지 않았다.

#### 사진은 지운다 — 행을 남기는 것과 별개다

**행을 남긴다고 사진까지 남기면 삭제가 아니다.** `challenge-verifications/` 는 공개 prefix라, 조회에서 빼도 **URL 을 아는 사람은 계속 볼 수 있다.** 지우고 싶어 지운 사람에게 그 상태는 삭제가 아니다.

미참조 이미지 정리를 위해 이 prefix 에 `DeleteObject` 권한이 이미 있으므로 그 자리에서 지운다.

**남는 것은 죽은 `image_url` 이다.** `NOT NULL` 이라 비울 수 없다. 지저분하지만 **동작에는 문제가 없다** — 그 행은 조회에서 빠지고, 당일 재인증으로 덮어쓰면 새 key 로 바뀐다. 컬럼을 `NULL` 허용으로 바꾸는 것은 얻는 것에 비해 파급이 크다.

**대신 미참조 정리의 참조 집계에서는 뺀다.** `deleteQuietly` 는 실패를 삼키므로 그 자리에서 못 지우는 경우가 있는데, 참조로 세면 정리 배치가 그것을 가져가지 못해 **지운 사진이 공개 prefix 에 영영 남는다.** 두 번째 방어선이 첫 번째 실패를 못 받는 셈이다. 다시 올리면 승격이 새 key 를 만들므로 옛 key 가 되살아나 쓰이는 일은 없다.

#### 내려간 글에는 좋아요·신고·메모 수정을 허용하지 않는다

**피드에 없는 글이다.** 좋아요가 쌓이면 다시 올려 되살아났을 때 엉뚱한 수를 달고 나타나고, 이미 내려간 글을 신고해 숨김 임계값을 채우는 것도 막아야 한다. 메모 수정도 같다 — 보이지 않는 글을 고칠 이유가 없다.

**다만 조회 자체에서 빼지는 않는다.** 삭제가 같은 조회를 쓰는데 그쪽은 내려간 글을 찾아야 하기 때문이다(두 번 지워도 성공으로 답한다). 서비스에서 거른다.

#### 좋아요·신고는 그대로 둔다

둘 다 `challenge_verification_id` 가 `NOT NULL` 이다. **하드 삭제였다면 함께 지워야 했지만, 소프트 삭제는 그 문제를 만들지 않는다.**

특히 **신고 이력은 남아야 한다.** 신고당한 글을 작성자가 지워 신고 기록까지 없애는 것은 곤란하다. 소프트 삭제는 그것을 자연히 지킨다.

#### 신고로 가려진 글은 지울 수 없다

메모 수정과 같은 기준이다. **본인에게도 안 보이는 글**이므로 그 위의 동작을 허용하지 않는다. 가려진 글을 지워 신고 누적을 회피하는 길도 함께 막힌다.

#### 권한과 경로

- **본인 글만.** 남의 글은 없는 글과 같은 `404` 로 답한다 — 존재 여부를 알려 주지 않는다
- **경로의 챌린지와 인증의 챌린지를 대조**한다. 메모 수정이 이미 하는 방식을 그대로 쓴다
- 이미 지운 글을 다시 지우면 **성공으로 답한다.** 삭제는 멱등한 편이 클라이언트가 다루기 쉽다

#### 스트릭 계산을 건드리지 않는다

A 를 택하면 `member_challenge` 는 손대지 않는다. 되돌리기 로직이 아예 필요 없다.

**"올려서 기록만 챙긴 뒤 지우기" 는 재화 회수가 막는다.** 인증이 심사를 통과하면 재화를 지급하고, 글을 내리면 그 지급분을 회수한다(재화 이슈). **회수는 시간과 무관하다** — 한 달 전 글을 지워도 적용된다.

> **스트릭을 시간 기준으로 깎는 방식은 검토했다가 접었다.** "당일 삭제만 뺀다" 로 두면 **하루만 기다리면 우회**되고, 기간을 넓혀도 그만큼 기다리면 된다 — 어느 값을 잡아도 마찬가지다.
>
> 반대로 소급해서 전부 빼면 **최근 글을 지울 때 스트릭이 붕괴한다.** 30일 연속에서 29일째를 내리면 마지막 연속 구간이 하루뿐이라 30 → 1 이 된다. 어제 사진에 얼굴이 나온 것을 알아채고 내린 사람에게 한 달치가 사라진다.
>
> **사진을 올려 심사를 통과했다면 그 사람은 루틴을 실제로 했다.** 공개를 원치 않아 내렸다고 "며칠째 이어왔다" 는 사실까지 부정할 근거는 약하다. 재화를 도로 내놓는 것으로 충분하다고 보았다.

> **AI 심사 보류가 반려로 확정되는 경우는 다르다.** 그쪽은 인증이 실제로 취소되는 것이라 스트릭을 되돌려야 한다. 두 이슈가 같아 보이지만 여기서 갈린다 — **삭제는 글을 내리는 것이고, 반려는 인증을 취소하는 것이다.**


### 미참조 이미지 정리에 참조처를 등록해야 한다

새 용도(prefix)마다 `MediaReferenceSource` 구현체를 등록하지 않으면 **그 사진은 영영 지워지지 않는다.** 담당자가 없는 용도는 정리 배치가 목록조차 훑지 않기 때문이다(안전한 방향으로 실패한다). 테이블을 만들 때 함께 등록한다.

### 확정된 정책

| # | 항목 | 결정 |
| --- | --- | --- |
| 1 | 그룹 인증에 사진 | **필수** |
| 2 | 그룹의 시간대 제약 | **유지** — 기존 그룹 관행을 따른다 |
| 3 | 그룹 재인증 | **막는다**(409). 챌린지의 재인증은 "이미 통과한 인증의 사진 교체"인데 그룹에는 그럴 이유가 없다 |
| 4 | 개인 루틴 인증 가능일 | **스케줄된 요일만.** 루틴에 정해진 날짜·시간이 있다 |
| 5 | 그룹 AI 심사 | **붙이지 않는다** |
| 6 | 그룹 신고 | **없다** |
| 7 | 개인 루틴 완료 표시 | **표시한다** |

이 결정들이 설계를 상당히 단순하게 만든다.

#### 그룹 — 기존 완료 경로를 인증 API 가 대체한다

사진을 필수로 하면 "사진 없이 완료시키는 경로"가 우회로가 되는데, **확인해보니 그 경로에 컨트롤러가 없다.** `GroupRoutineAssignmentCommandService.completeAssignment(assignmentId, verifiedAt)` 가 서비스에만 있고 API 로 열려 있지 않다.

즉 **인증 API 가 그 자리를 그대로 채우면 된다.** 우회로가 생기는 것이 아니라, 원래 비어 있던 진입점을 인증이 채우는 모양이다. 그 메서드가 이미 `verifiedAt` 을 받는 것도 이 흐름을 전제한 설계로 보인다.

```
POST /api/groups/{gid}/routines/{rid}/verifications
  → 사진 검증
  → group_routine_verification 저장
  → completeAssignment(assignmentId, verifiedAt)   ← 기존 메서드 재사용
```

시간대 제약·중복 완료 차단(409)은 그 메서드가 이미 하고 있으므로 **인증 쪽에서 다시 구현하지 않는다.**

#### 개인 — 완료 표시가 조회에 붙는다

`RoutineResDTO.Routine` 은 지금 `routineId · categoryId · name · endTime · repeatDays · alarmTime` 만 담는다. **완료 여부를 담을 자리가 없다.** 필드를 추가해야 한다.

> ⚠️ **PR #100(홈 메인)과 겹친다.** 그쪽이 이 DTO 를 홈 화면에 그대로 쓰고 있어, 필드 추가 시 조율이 필요하다.

완료 여부는 `member_routine_verification` 에 오늘 날짜 행이 있는지로 판단한다. **루틴 목록 조회에서 루틴마다 따로 물으면 N+1 이 되므로**, 오늘자 인증을 한 번에 가져와 맞춰야 한다(챌린지 피드의 좋아요 집계와 같은 방식).

#### 그룹에는 유해물 대응 수단이 없다

5·6번 조합의 결과다. AI 심사도 신고도 없으므로, 방에 부적절한 사진이 올라와도 **멤버가 할 수 있는 것이 없다.**

방 멤버끼리만 보는 사적 공간이라는 판단으로 이해했다. 다만 그룹이 초대코드로 열려 있어 모르는 사람이 들어올 수 있다는 점은 남겨 둔다. 문제가 되면 신고를 먼저 붙이는 것이 AI 심사보다 싸다.

### 탈퇴·삭제 시 처리가 정해져 있지 않다

새 테이블 2개에 대해 정해야 한다.

- **탈퇴**: 정책은 "공동 공간의 기존 인증·채팅은 작성자를 `탈퇴한 사용자`로 바꾸어 유지"다. 그룹 루틴 인증이 여기 해당한다고 보면 유지다. **개인 루틴 인증은 공동 공간이 아니므로 이 문구가 답하지 않는다.**
- **루틴 비활성화**: `member_routine.active = false`가 되면 그 루틴의 인증 행은? 기록이므로 남기는 쪽이 자연스럽지만 명시가 필요하다.
- **그룹 탈퇴·해체**: 방을 나가거나 방이 사라지면 그 방의 인증 사진은? 경로에 `groupId`가 들어가므로 prefix 단위 정리가 가능하다.

### 그 밖에 정해둘 것

- **회차 개념이 없다.** 챌린지의 `participation_round`에 해당하는 것이 루틴에는 없다. 이탈 후 재참여 같은 흐름이 없으므로 그대로 두면 된다.
- **마이그레이션 버전은 구현 직전에 확인한다.** `db/migration/`과 열린 PR을 확인하고 현재 마지막
  versioned migration 다음 번호를 사용한다.
- **staging prefix**: 심사 전 사진을 비공개에 두는 문제는 별도로 다루는데, 그룹·개인 인증은 **애초에 비공개 prefix**라 그 문제가 없다. 챌린지만 해당한다.

### 단계

동작이 바뀌지 않는 것과 새 기능을 갈라 각각 독립적으로 머지한다.

| 단계 | 내용 | 동작 변화 |
| --- | --- | --- |
| 0 | 이 문서 합의 | 없음 ✅ **완료** |
| 1 | presigned GET(비공개 미디어 서빙) + 조회 API | 새 API 추가 ✅ **완료** |
| 2 | 챌린지 인증을 `verification` 도메인으로 이동 | **없음** — 테스트가 그대로 통과하는 것이 검증 |
| 3 | 그룹·개인 인증 테이블·API | 새 기능 ✅ **완료** |

> 3단계가 1단계보다 먼저 나갔다. 그 사이 **인증 사진은 저장되지만 아무도 볼 수 없는 상태**였다 —
> 선행이라고 적어 둔 것을 건너뛰면 기능이 반만 나간다는 것을 실제로 확인한 셈이다.

3단계에는 **PR #100(홈 메인)과의 조율**이 딸린다. 개인 루틴 완료 표시가 `RoutineResDTO.Routine`에 필드를 더하는데, 그쪽이 그 DTO를 홈 화면에 그대로 쓴다.

## DDL 확인 필요 사항

- 기본 키는 정의되어 있으나, `id`의 자동 생성 전략(`AUTO_INCREMENT` 등)은 DDL에 없다.
- **기존 테이블에는** 유니크 제약조건과 일반 인덱스가 정의되어 있지 않다. 예를 들어 `member.email`, `member_order.order_number`, 조회에 자주 쓰이는 외래 키 및 `deleted_at`은 요구사항에 따라 인덱스 검토가 필요하다. (챌린지 테이블은 예외로, 해당 절에 제약과 인덱스를 명시했다.)
- `payment.member_order_id`에는 유니크 제약조건이 없으므로, 현재 정의상 하나의 주문이 여러 결제 레코드를 가질 수 있다.
- `member_routine`은 이름과 달리 회원 식별자가 없으며 다른 테이블과의 외래 키도 없다.
- 챌린지 테이블은 위 표에 유니크 제약과 인덱스를 명시했다. 기존 테이블도 같은 수준으로 보완이 필요하다.
- 소프트 삭제(`deleted_at`)와 유니크 제약은 함께 쓰면 충돌한다. 삭제된 행도 유니크 제약에 남기 때문이다. `challenge_verification`은 덮어쓰기 방식을 택해 이 문제를 피했으나, **기존 테이블 중 `deleted_at`과 유니크 제약을 함께 가진 것이 있다면 같은 문제가 있는지 점검이 필요하다.**
- 챌린지 인증 사진은 외부 스토리지(S3)에 저장하고 DB에는 **오브젝트 key만** 둔다(전체 URL을 저장하지 않는다). CDN·버킷 도메인이 바뀌어도 저장된 데이터를 마이그레이션하지 않기 위해서다. 읽기 URL은 조회 시점에 `aws.s3.public-base-url` + key로 조립한다.
  - `public-base-url`을 **미주입하면 버킷·리전으로 path-style S3 주소(`https://s3.<리전>.amazonaws.com/<버킷>`)를 계산해 기본값으로 쓴다.** 설정 하나가 없다고 앱 전체가 못 뜨는 것을 막기 위해서다(특히 merge=즉시 배포 환경). 기본값이 진짜 URL이라 피드의 사진 URL이 null로 새지 않는다. 다만 **버킷이 비공개면 그 URL은 403**이므로, 사진을 실제로 노출하려면 버킷을 공개하거나 CloudFront를 앞에 두고 `public-base-url`을 그 도메인으로 덮어쓴다(이 서빙 방식 확정은 별도 이슈에서 다룬다).
  - path-style을 기본으로 쓰는 이유: 가상호스팅(`<버킷>.s3.<리전>...`)은 버킷명에 `.`이 있으면 와일드카드 TLS 인증서(`*.s3.<리전>...`)와 맞지 않아 조회가 깨진다. path-style은 버킷명에 무관하게 안전하다.
  - 컬럼명이 `image_url`이지만 실제로 담기는 값은 key다. 이름과 내용이 어긋나 있으므로 마이그레이션 도구를 도입할 때 `image_key`로의 개명을 함께 검토한다.

### 미디어 S3 key 규칙 (#39)

미디어를 쓰는 도메인이 늘어나므로(챌린지 인증·프로필·개인 루틴·그룹 루틴·그룹 채팅) key 규칙을 여기서 한 번 정한다. **새 용도를 추가할 때 이 절을 먼저 갱신하고 코드를 쓴다.**

```text
공개
  challenge-verifications/2026/07/30/{UUID}.jpg
  profiles/2026/07/30/{UUID}.jpg

비공개
  challenge-verifications-staging/2026/07/30/{UUID}.jpg
  housework-completions/{memberId}/2026/07/30/{UUID}.jpg
  group-routine-completions/{groupId}/2026/07/30/{UUID}.jpg
  group-chats/{groupId}/2026/07/30/{UUID}.jpg
  chat-emoticons/2026/07/30/{UUID}.png
```

규칙은 두 줄이다.

- **모든 용도** — `{용도 prefix}/{yyyy}/{MM}/{dd}/{UUID}.{확장자}`
- **회원·그룹 비공개 용도** — 날짜 앞에 **접근 범위 식별자 하나**를 더 둔다
- **서비스 소유 자산** — 회원·그룹 범위가 없으므로 접근 범위 식별자를 넣지 않는다. 애플리케이션이 자산 사용 권한과 조회 URL 발급을 통제한다.

`chat-emoticons/`는 서비스가 등록한 정적 이모티콘 자산 전용 prefix다. 현재 1차 채팅 범위에는 사용자 이미지·파일 업로드가 없으므로 `group-chats/{groupId}/...`는 향후 채팅 첨부 미디어를 위한 규칙으로만 둔다. 이모티콘은 그룹 소유 미디어가 아니므로 `groupId`나 `memberId`를 key에 포함하지 않는다.

#### 심사가 붙는 용도는 대기 prefix로 먼저 받는다

**챌린지 인증만 해당한다.** 사진이 AI 심사를 거치는 유일한 용도이면서, 통과 후에는 전체 공개이기 때문이다.

```text
① 발급   업로드 URL 은 challenge-verifications-staging/… 으로 서명한다 (비공개)
② 업로드 클라이언트가 그 URL 로 PUT 한다 — 이 시점에는 아무도 못 본다
③ 심사   통과하면 challenge-verifications/… 으로 복사한다 (대기본은 아직 둔다)
         반려하면 대기본을 지운다
④ 삭제   저장이 커밋된 뒤에 대기본을 지운다. 실패해도 넘어간다
⑤ 공개   공개 URL 은 저장 응답으로만 나간다 (오브젝트는 ③부터 있지만 주소를 아는 사람이 없다)
```

**대기본 삭제를 저장 뒤로 두는 이유**는 [승격은 저장을 사이에 두고 순서가 정해져 있다](#승격은-저장을-사이에-두고-순서가-정해져-있다)에 적었다. 복사 직후에 지우면 저장보다 앞서게 되어 그 절이 정한 순서와 어긋난다.

**날짜는 그대로 두고 UUID 는 새로 뽑는다.**

```text
대기  challenge-verifications-staging/2026/07/30/{UUID-A}.jpg
공개  challenge-verifications/2026/07/30/{UUID-B}.jpg
```

날짜를 유지하는 것은 정리 배치 때문이다. 미참조 정리가 공개 prefix 를 **날짜별로 나열해** 훑으므로, 승격본이 다른 날짜에 들어가면 그 날짜 창을 벗어난다.

**UUID 를 새로 뽑는 것은 덮어쓰기를 막기 위해서다.** prefix 만 갈아끼우면 같은 대기 key 가 늘 같은 공개 key 가 된다. 대기본 삭제는 실패해도 넘어가므로(④) 대기본이 남을 수 있고, 그 상태에서 만료 전 presigned URL 로 같은 key 에 다른 사진을 올려 다시 승격하면 **이미 DB 가 참조하는 공개본을 덮어쓴다.** 지난 인증의 사진이 조용히 바뀐다.

`copySourceIfMatch` 로는 막지 못한다 — 그때 심사한 것은 새 바이트라 조건이 그대로 통과한다. 목적지에 조건을 걸려 해도 `CopyObjectRequest` 에는 source 조건만 있고 목적지 조건이 없으며, 미리 `headObject` 로 확인하는 방식은 확인과 복사 사이가 열려 원자성을 얻지 못한다. **덮어쓸 대상을 만들지 않는 편이 확실하다.**

> 두 번 승격되면 공개본이 둘 생긴다. 둘째는 아무도 참조하지 않아 미참조 정리가 가져간다 — ③이 실패해 공개본만 남는 경우와 같은 경로다.

> **key 형식이 같다고 정리 방식까지 같지는 않다.** 날짜 형식이 같아 배치가 훑는 모양은 같지만, **미참조 정리(orphan sweep)의 대상은 공개 prefix 뿐**이다. 대기본은 참조가 없는 것이 정상이라 그 대조에 넣으면 안 되고, 나이 기반 수명 주기로만 지운다 — 아래 [대기 오브젝트는 나이로 지운다](#대기-오브젝트는-나이로-지운다) 참고.

##### 승격은 저장을 사이에 두고 순서가 정해져 있다

**순서를 어기면 사진이 사라지거나 심사 안 된 사진이 공개된다.** 구현이 임의로 정할 자리가 아니라서 여기서 못 박는다.

```text
① AI 심사 통과
② 승격      대기 → 공개 로 복사(S3 내부 복사)
③ 저장      트랜잭션 안에서 image_url 에 공개 key 를 저장
④ 대기본 삭제  실패해도 무시한다
```

**커밋 지점은 ③이다.** 각 단계가 실패하면 이렇게 된다.

> **오브젝트가 공개 prefix 에 놓이는 것은 ②이고, 그 주소가 밖으로 나가는 것은 ③이다.** 둘 사이에는
> 공개된 파일이 있지만 **아무도 그 주소를 모른다** — key 가 UUID 이고 익명 `ListBucket` 이 막혀 있어
> 열거도 안 된다. ③이 실패하면 그 오브젝트는 주소가 한 번도 나가지 않은 채 미참조로 남아 정리 배치가
> 가져간다. 이 PR 이 없애려는 문제(반려된 사진의 링크가 살아 있는 것)와는 다른 상태다.

| 실패한 곳 | 사용자에게 | 남는 것 | 누가 치우나 |
| --- | --- | --- | --- |
| ② 복사 | 저장 실패 응답 | 대기본 | 수명 주기 |
| ③ 저장 | 저장 실패 응답 | 공개본(참조 없음) + 대기본 | 미참조 정리 + 수명 주기 |
| ④ 삭제 | **성공** — 인증은 저장됐다 | 대기본 | 수명 주기 |

**어느 경우에도 "사진이 사라졌는데 인증은 저장됨"이 되지 않는다.** 남는 방향의 실패만 있고, 남은 것은 전부 치우는 주인이 있다.

④를 성공 조건에 넣지 않는 이유는, 대기본 하나를 못 지운 것 때문에 **이미 저장된 인증을 실패로 되돌리는 것이 더 나쁘기** 때문이다. 그 뒤처리는 수명 주기가 맡는다.

**반려면 대기본만 지운다.** 지금은 심사 반려 시 공개 오브젝트를 지우는데(`deleteQuietly`), 승격 전이므로 지울 대상이 대기본으로 바뀐다. 공개본은 애초에 만들어지지 않는다.

##### 왜 필요한가

**심사 전 사진이 공개 URL 로 열려 있었다.** 발급 응답에 공개 URL 이 함께 나가고 업로드 순간부터 그 주소가 살아 있어서, **심사에서 반려돼도 그 사이 열람하거나 공유한 것은 회수되지 않았다.** AI 심사가 "공개 전에 거르는" 장치로 동작하지 않고 이미 공개된 것을 사후에 치우는 것에 가까웠다.

범위를 과장하지 않기 위해 적어 둔다 — key 가 UUID 라 추측할 수 없고 익명 `ListBucket` 도 막혀 있어, 그 URL 을 아는 사람은 **업로더 본인과 그가 공유한 상대뿐**이다. "누군가 유해물을 올려 피드에 노출된다"가 아니라 **"반려된 사진의 링크가 잠시 우리 도메인에서 살아 있다"** 가 정확한 표현이다. 낮지만 0 은 아니다.

##### 발급 응답에서 공개 URL 이 빠진다

**클라이언트 변경이 따른다.** 대기 prefix 로 받으면 발급 시점에는 공개 주소가 존재하지 않는다.

| | 지금 | 바뀐 뒤 |
| --- | --- | --- |
| presigned 발급 응답 | 공개 URL 을 함께 준다 | **주지 않는다** |
| 인증 저장 응답 | `imageUrl` | 그대로 — **여기서 받는다** |

인증 저장 응답에는 이미 `imageUrl` 이 있으므로 새 필드가 생기지는 않는다. 없어지는 쪽만 맞추면 된다.

> 참고로 **발급 응답의 그 URL 은 비공개 용도에서는 원래도 틀린 값이었다.** 용도와 무관하게 공개 주소를 조립해 내려주는데, 그룹·개인 루틴 인증은 버킷 정책이 열려 있지 않아 그 주소로 열면 403 이다. 이번 변경으로 그 값이 사라지면 이 오류도 함께 없어진다.

##### 대기 오브젝트는 나이로 지운다

승격되지 않은 대기본은 **DB 에서 참조되지 않는 것이 정상**이다. 그래서 미참조 정리 배치의 "S3 목록과 DB 대조" 방식이 여기서는 맞지 않는다 — 대조하면 전부 미참조로 나온다.

**S3 수명 주기 규칙으로 나이 기반 삭제**를 건다. 심사는 업로드 직후에 끝나므로 오래 남아 있는 대기본은 어차피 버려진 것이다.

> ⚠️ **기간은 보류 상한보다 길어야 한다.** 심사가 장애로 판정을 못 하면 보류하기로 하면서(위 [심사가 장애로 답을 못 주면 보류한다](#심사가-장애로-답을-못-주면-보류한다)) **대기본이 보류 기간 동안 살아 있어야 한다.** 상한 24시간에 수명 주기 3일이 그 조합이다. **1일로 먼저 걸어 두고 나중에 늘리면 그 사이 보류 건이 지워진다.**

##### 권한이 하나 더 필요하다

승격은 S3 내부 복사라 전송비가 들지 않는다. 필요한 권한을 하나씩 따져 보면 **모자란 것은 삭제뿐이다.**

| 하는 일 | 필요한 권한 | 현재 정책 | 판정 |
| --- | --- | --- | --- |
| 대기 prefix 로 presigned PUT 서명 | `s3:PutObject` | `lirouti-prod-bucket/*` | ✅ 이미 됨 |
| 업로드 바이트 검증(Range GET) | `s3:GetObject` | `lirouti-prod-bucket/*` | ✅ 이미 됨 |
| 승격 복사(대기 읽기 → 공개 쓰기) | `s3:GetObject` + `s3:PutObject` | `lirouti-prod-bucket/*` | ✅ 이미 됨 |
| **대기본 삭제** | `s3:DeleteObject` | `challenge-verifications/*` **로만** | ❌ **막힘** |

`PutObject`·`GetObject` 는 버킷 전체에 열려 있어 prefix 를 하나 늘려도 그대로 동작한다. 반면 `DeleteObject` 는 `MediaCleanupDelete` 문에서 공개 prefix 로 좁혀져 있다. **`challenge-verifications/*` 는 `challenge-verifications-staging/…` 을 포함하지 않는다** — 접두사 뒤의 `/` 때문이다. 버킷 정책의 공개 규칙이 대기본에 걸리지 않는 것도 같은 이유다.

```jsonc
// deploy/iam-policy.json — MediaCleanupDelete 의 Resource 를 배열로 바꾼다
"Resource": [
  "arn:aws:s3:::lirouti-prod-bucket/challenge-verifications/*",
  "arn:aws:s3:::lirouti-prod-bucket/challenge-verifications-staging/*"
]
```

**적용 시점은 구현 PR 보다 앞이다.** 정책은 콘솔에서 사람이 넓히는 것이라 배포와 함께 나가지 않는다. 순서가 뒤바뀌면 **승격은 되는데 대기본만 계속 쌓인다** — 수명 주기가 결국 지우므로 장애는 아니지만, 그동안 지워야 할 것이 안 지워지는 상태가 된다.

목록 권한(`ListBucket`)은 넓히지 않는다. 대기본은 나이 기반 수명 주기로 지우므로 **앱이 목록을 훑을 일이 없다.**

##### 이미 올라간 사진은 옮기지 않는다

기존 `challenge-verifications/` 객체는 그대로 둔다. 그것들은 이미 심사를 지났거나 심사가 없던 시절의 것이라 대기 상태로 되돌릴 이유가 없다. **마이그레이션 없음.**

#### 리프는 항상 UUID다

파일명에 클라이언트가 보낸 값을 쓰지 않는다(경로 조작 방지). UUID라 다른 회원의 미디어 경로를 유추할 수 없고, 공개 prefix의 버킷 정책이 **익명 접근자에게** `s3:ListBucket`을 주지 않으므로 외부에서 열거하는 것도 불가능하다.

> 열거할 수 있는 것은 **서버뿐이다.** 앱의 IAM 역할은 미참조 이미지 정리를 위해 `challenge-verifications/` 아래에 한해 `ListBucket`을 갖는다. 버킷 정책(익명)과 IAM 역할(서버)은 서로 다른 주체이므로 혼동하지 않는다.

#### 날짜는 "업로드일"이다

presigned URL 발급 시점의 KST 날짜다. **인증일(`verified_date`)과 다를 수 있다** — 23:59에 발급받아 00:01에 인증하면 경로는 어제, DB는 오늘이 된다. 발급이 인증보다 먼저 일어나므로 인증일을 경로에 담는 것은 불가능하다. **경로의 날짜를 업무 판정에 쓰지 않는다.** 판정은 항상 DB 컬럼을 본다.

날짜를 넣는 이유는 **미참조 이미지 정리(#19)** 다. 날짜 prefix가 있으면 `ListObjects(prefix=".../2026/07/29/")`로 하루치만 훑을 수 있다. 없으면 용도 전체를 스캔해야 한다. 프로필도 교체 시 이전 사진이 고아가 되므로 같은 규칙을 적용해, 정리 로직이 용도마다 갈리지 않게 한다.

#### 정리 배치가 "쓰이는 중"을 판단하는 방법 (#19)

미참조 정리는 S3 목록과 **DB를 대조**한다. 그래서 **미디어 key를 담는 컬럼이 새로 생기면 그 사실을 정리 쪽에 알려줘야 한다.** 알려주지 않으면 그 컬럼이 가리키는 파일이 지워진다.

알리는 방법은 `MediaReferenceSource` 구현체다. 어떤 용도(prefix)를 책임지는지, 후보 key 중 어느 것이 쓰이는지를 답한다. **어떤 구현체도 담당하지 않는 용도는 정리 대상에서 아예 빠진다** — 담당자가 없다는 건 "무엇이 참조되는지 아무도 모른다"는 뜻이라, 안전한 쪽(안 지움)으로 실패하게 만들었다.

현재 미디어 정리 구현체가 참조하는 key 컬럼은 다음과 같다.

| 테이블·컬럼 | 담당 구현체 | 비고 |
| --- | --- | --- |
| `challenge_verification.image_url` | `ChallengeMediaReferenceSource` | 정리의 실제 대상 |
| `challenge.image_url` | `ChallengeMediaReferenceSource` | 지금은 전부 `NULL`. 대표 이미지를 채울 때를 대비해 미리 포함 |
| `chat_emoticon.asset_key` | `ChatMediaReferenceSource` | 활성·비활성 이모티콘 모두 기존 메시지 보존을 위해 참조 중으로 취급 |
| `achievement.badge_image_key` | `AchievementMediaReferenceSource` | 비활성 업적도 DB에 key가 남아 있으면 참조 중으로 취급 |

**탈퇴·신고로 숨겨진 인증의 사진도 "쓰이는 중"으로 친다.** 행이 남아 있으면 파일도 살아 있는 것이다. 탈퇴 회원 사진을 지우는 것은 별개 정책이다(#70).

`profiles/`는 현재 담당 구현체가 없다 — `MediaPurpose`에는 있지만 발급된 적이 없고 그 key를 담는 컬럼도 없다. **프로필 업로드를 구현할 때 컬럼을 추가하면서 참조처도 함께 등록해야 한다.**

**대기 prefix(`challenge-verifications-staging/`)는 여기에 등록하지 않는다.** 승격되지 않은 대기본은 DB 가 참조하지 않는 것이 정상이라, 대조 방식이 애초에 맞지 않는다. 대신 나이 기반 수명 주기 규칙으로 지운다 — 근거는 [심사가 붙는 용도는 대기 prefix로 먼저 받는다](#심사가-붙는-용도는-대기-prefix로-먼저-받는다)에 적었다. **담당자가 없는 유일한 정상 사례**이므로 빠뜨린 것으로 오해하지 않도록 여기에도 남긴다.

아래 두 용도는 [인증(verification) 도메인](#인증verification-도메인--설계안-합의-필요)에서 **테이블·API 가 이미 추가됐다.** 다만 **아직 `MediaReferenceSource` 구현체가 없다.** 위 표의 구현체가 담당하는 용도와 달리, prefix도 목표 형태이지 현재 형태가 아니다.

> ⚠️ **이 두 용도의 고아 객체는 미참조 정리 배치로 삭제되지 않는다.** 담당자가 없는 용도는 배치가 목록조차 훑지 않기 때문이다(위 원칙 — 안전한 쪽으로 실패한다). 다른 수단(수동 삭제, 버킷 수명 주기 규칙)까지 막는 것은 아니지만, 지금 그런 수단은 없다.
>
> **고아가 실제로 생긴다.** presigned URL 로 업로드한 뒤 인증 저장이 실패하거나(형식·시간대·중복 검증) 롤백되면 그 오브젝트는 DB 에서 참조되지 않은 채 남는다. 챌린지는 반려 시 `deleteQuietly` 로 즉시 지우고 정리 배치가 뒤를 받치지만, 이 둘은 양쪽 다 없다.
>
> **당장 위험하지는 않다.** 삭제되면 안 되는 파일이 지워지는 방향이 아니라 지워져야 할 파일이 남는 방향이고, 인증 건수도 아직 적다. 다만 **누적되는 종류의 문제라 미루면 정리 비용만 커진다** — 실사용자를 받기 전에 구현체를 등록해야 한다.

| 용도 | 지금 발급되는 prefix | 목표 prefix | 참조 컬럼 | 참조처 등록 |
| --- | --- | --- | --- | --- |
| 그룹 루틴 인증 | `group-routine-verifications/` | `group-routine-verifications/{groupId}/` | `group_routine_verification.image_url` | ❌ 미등록 |
| 개인 루틴 인증 | `member-routine-verifications/` | `member-routine-verifications/{memberId}/` | `member_routine_verification.image_url` | ❌ 미등록 |

#### 범위 식별자는 "누가 볼 수 있는가"의 단위다

"누가 만들었는가"가 아니다.

| 용도 | 볼 수 있는 사람 | 범위 식별자 |
| --- | --- | --- |
| 개인 루틴 인증 | 본인만 | `memberId` |
| 그룹 루틴 인증 | 그 방 멤버 전원 | `groupId` |
| 그룹 채팅 이미지 | 그 방 멤버 전원 | `groupId` |

**그룹 경로에 작성자 `memberId`를 넣지 않는다.** A가 올린 것도 B가 봐야 하므로 범위는 방 단위여야 하고, 그 아래를 `memberId`로 더 쪼개면 접근 제어에 기여하는 바가 없다. 반면 URL이 유출되면 "누가 올렸는지"까지 흘린다.

> 이 근거는 원래 CloudFront signed cookie를 전제로 썼다(쿠키가 경로 prefix로 범위를 잡으므로). **실제 서빙은 presigned GET으로 구현했고**(조직 SCP에 CloudFront가 없다, `deploy/README.md`의 [미디어 서빙] 절) 서명이 오브젝트 단위라 경로가 범위를 정하지 않는다. 그래도 결론은 그대로다 — 작성자를 경로에 넣으면 URL이 새는 순간 그것까지 새고, 얻는 것은 없다.

범위 식별자에는 두 번째 값어치가 있다. **그룹이 삭제될 때 prefix 하나로 정리된다.** 탈퇴 정책이 "혼자 있는 방은 삭제한다"이므로 그룹 삭제는 실제로 일어나는 흐름이다. `groupId`가 없으면 방이 사라진 뒤 그 방의 key를 DB에서 되짚어야 한다.

#### 공개 용도에는 식별자를 넣지 않는다

`challenge-verifications/`는 공개 prefix라(#66) **경로가 URL에 그대로 노출된다.** `challengeId`·`memberId`를 넣으면 URL만 보고 "412번 회원이 3번 챌린지를 했다"를 알 수 있다.

얻는 것도 없다. 탈퇴 회원 사진 삭제(#70)는 prefix 없이도 된다 — `member → member_challenge → challenge_verification.image_url`로 DB가 key를 정확히 안다.

#### 발급 API가 받는 값

비공개 용도는 **발급 시점에 범위 식별자를 받아야** 한다. `MediaPurpose`가 공개 여부를 알고 있으므로, 비공개인데 식별자가 없으면 발급을 거부한다.

식별자는 클라이언트가 보낸 값이라 그대로 믿지 않는다. **저장 시점에 경로의 식별자가 실제 대상과 맞는지 대조한다** — 챌린지 인증에서 `validateMediaKey`가 발급 규칙을 대조하는 것과 같은 방식이다.

> **대기 prefix 는 비공개인데 식별자가 없다.** 위 규칙과 어긋나 보이지만 의도한 것이다. 식별자를 두는 이유는 "누가 볼 수 있는가"를 경로로 가르기 위해서인데, 대기본은 **익명 사용자도 클라이언트도 읽을 수 없다**(서버만 바이트 검증·심사·승격을 위해 읽는다). 승격되면 전체 공개가 되므로 그때도 가를 대상이 없다.
>
> **대기는 챌린지 인증의 비공개 상태이고, 승격된 뒤에 공개 상태가 된다.** 이 문장을 "대기도 공개 용도"로 읽으면 안 된다 — `MediaPurpose` 의 `publicRead` 는 **버킷 정책과 짝**이라, 대기에 그 값을 참으로 주면 정책이 열지 않은 주소를 공개인 양 내려주게 된다.
>
> 클라이언트가 발급을 요청할 때 보내는 용도는 그대로 챌린지 인증이다. **어느 prefix 로 받을지는 서버가 정한다** — API 로 드러내지 않는다.

#### 기존 flat key는 마이그레이션하지 않는다

`challenge-verifications/{UUID}.jpg` 형태로 이미 올라간 오브젝트가 있다. 읽기 URL은 `public-base-url + key` 단순 결합이라 **두 형태가 함께 동작한다.** 버킷 정책도 `challenge-verifications/*`로 걸려 있어 중첩 경로까지 덮는다. 옮길 이유가 없다.

다만 **key 형식 검증은 두 형태를 모두 통과시켜야 한다.** 형식이 바뀌기 전에 발급된 key가 바뀐 뒤에 저장될 수 있기 때문이다(presigned URL 유효 시간이 5분이라 창은 좁다).
