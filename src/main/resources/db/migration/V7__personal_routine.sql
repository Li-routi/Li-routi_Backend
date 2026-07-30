-- 개인 루틴 도메인 테이블.
--
-- 그룹 루틴(V2)과 나란히 놓이는 구조다. 두 도메인이 routine_category 마스터를 공유하고,
-- 개인 루틴은 그 위에 기본 제공 루틴 마스터(routine_template)와 회원 소유 루틴
-- (member_routine, member_routine_schedule)을 얹는다.
--
-- V1·V2와 같은 이유로 IF NOT EXISTS를 쓰고, 제약·인덱스 이름과 컬럼 순서는 Hibernate가
-- 만드는 형태에 맞춘다(ddl-auto=validate가 엔티티와 대조하기 때문이다).
--
-- FK 의존 순서: routine_category → routine_template → member_routine → member_routine_schedule
-- (routine_category는 V2가, member는 V1이 먼저 만들어 둔다)

-- 1) routine_category 확장
--
-- V2에서는 "앱이 관리하는 마스터"였다. 여기서 고정 카테고리와 회원이 추가한 사용자 카테고리를
-- 함께 담는 테이블로 바뀐다. 구분은 member_id의 NULL 여부다(NULL = 고정).
--
-- 컬럼을 각각 IF NOT EXISTS 없이 추가한다 — MySQL은 ADD COLUMN IF NOT EXISTS를 지원하지
-- 않는다. V__ 마이그레이션은 한 번만 실행되므로 문제되지 않지만, ddl-auto=update 시절에
-- 이 컬럼이 이미 생긴 로컬 DB가 있다면 이 마이그레이션은 실패한다. 그런 DB는 없다 —
-- 이 컬럼들은 지금 이 PR에서 엔티티에 처음 들어간다.
ALTER TABLE `routine_category`
  -- 소유 회원. NULL이면 앱이 제공하는 고정 카테고리다.
  ADD COLUMN `member_id` bigint DEFAULT NULL,
  -- 사용자 카테고리의 색상 칩. 고정 카테고리와 "색 없음"은 NULL이다.
  -- HEX가 아니라 enum 이름을 저장한다. 실제 색은 테마·플랫폼마다 달라 클라이언트가 정한다.
  -- V1·V2의 다른 enum 컬럼과 같이 MySQL 네이티브 enum을 쓰고 값도 Hibernate가 만드는
  -- 순서(알파벳)로 둔다. 색을 추가할 때 ALTER가 필요한 대신, 없는 색이 들어오는 것을 DB가 막는다.
  ADD COLUMN `color` enum('BLACK','BLUE','GREEN','MAGENTA','ORANGE','RED','YELLOW') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  -- 목록 노출 순서. 고정 카테고리는 시드가 기획 순서를 넣고, 사용자 카테고리는 0으로 남아
  -- 생성 순서(id)로 정렬된다. 기존 행에 값을 채워야 하므로 DEFAULT 0으로 둔다.
  ADD COLUMN `display_order` int NOT NULL DEFAULT 0,
  ADD CONSTRAINT `FK_routine_category_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`);

-- 이름 유니크 범위를 "전역"에서 "소유자별"로 바꾼다. 회원 A와 회원 B가 같은 이름의
-- 카테고리를 각자 만들 수 있어야 하기 때문이다.
--
-- MySQL은 유니크 키에서 NULL을 서로 다른 값으로 취급하므로, 이 제약이 고정 카테고리
-- (member_id IS NULL)끼리의 이름 중복은 막지 못한다. 고정 카테고리는 R__seed_routine.sql이
-- 고정 id로만 넣으므로 실제로 중복이 생길 경로가 없다.
--
-- "사용자가 고정 카테고리와 같은 이름을 만들 수 없다"는 기획 규칙도 이 제약으로는 표현되지
-- 않는다(소유자가 다르므로 충돌하지 않는다). 그쪽은 애플리케이션에서 검증한다
-- (RoutineCategoryRepository.existsUsableName).
ALTER TABLE `routine_category`
  DROP INDEX `uk_routine_category_name`,
  ADD UNIQUE KEY `uk_routine_category_member_name` (`member_id`,`name`);

-- 2) 기본 제공 루틴 마스터
--
-- 루틴 추가 화면에서 카테고리마다 미리 보여 주는 목록이다. 목록의 단일 진실 공급원은
-- R__seed_routine.sql이고, 여기서는 구조만 만든다.
--
-- 시간·요일 컬럼이 없는 것은 의도적이다. 마감 시각(기본 23:59)과 반복 요일(기본 매일)은
-- 회원이 고르는 값이라 member_routine에 있다.
CREATE TABLE IF NOT EXISTS `routine_template` (
  `active` bit(1) NOT NULL,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `display_order` int NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_routine_template_category_name` (`category_id`,`name`),
  CONSTRAINT `FK_routine_template_category` FOREIGN KEY (`category_id`) REFERENCES `routine_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 개인 루틴
--
-- 기본 제공 루틴에서 만든 루틴과 회원이 직접 추가한 루틴을 한 테이블에 둔다.
-- routine_template_id가 있으면 앞쪽, NULL이면 뒤쪽이다.
--
-- start_time이 없다. 개인 루틴은 마감 시각만 정한다(설정 바텀시트에도 "마감시간"뿐이다).
-- 그룹 루틴이 요일마다 start~end 범위를 갖는 것과 다른 점이라 일정 테이블도 따로 둔다.
--
-- deleted_at을 두지 않았다. 삭제 API가 이 PR 범위 밖이라 소프트 삭제 정책이 정해지지
-- 않았고, 정해지지 않은 컬럼을 미리 만들면 그 컬럼을 아무도 채우지 않는 상태가 오래 남는다.
-- "활성 루틴 최대 30개" 규칙은 active로 센다.
CREATE TABLE IF NOT EXISTS `member_routine` (
  `active` bit(1) NOT NULL,
  `alarm_time` time DEFAULT NULL,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_time` time NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `routine_template_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  -- 같은 기본 루틴을 두 번 고를 수 없다. 화면의 체크박스는 한 번만 켜지지만 같은 요청이
  -- 두 번 도착하면(재시도·더블탭) 애플리케이션 검증만으로는 막을 수 없다.
  -- routine_template_id가 NULL인 직접 추가 루틴은 NULL이 서로 다른 값으로 취급되어 이
  -- 제약에 걸리지 않는다 — 사용자 루틴끼리 같은 이름을 허용하는 기획과 맞다.
  UNIQUE KEY `uk_member_routine_member_template` (`member_id`,`routine_template_id`),
  -- 활성 루틴 개수 세기와 회원별 목록 조회 경로다.
  KEY `idx_member_routine_member_active` (`member_id`,`active`),
  KEY `FK_member_routine_category` (`category_id`),
  KEY `FK_member_routine_template` (`routine_template_id`),
  CONSTRAINT `FK_member_routine_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FK_member_routine_category` FOREIGN KEY (`category_id`) REFERENCES `routine_category` (`id`),
  CONSTRAINT `FK_member_routine_template` FOREIGN KEY (`routine_template_id`) REFERENCES `routine_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 개인 루틴의 반복 요일
--
-- 그룹 루틴 일정과 달리 시간 범위가 없다. 마감 시각은 루틴 단위로 한 번만 정하므로
-- 이 테이블은 "어느 요일에 반복하는가"만 담는다.
CREATE TABLE IF NOT EXISTS `member_routine_schedule` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_routine_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `repeat_day` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_routine_schedule_day` (`member_routine_id`,`repeat_day`),
  CONSTRAINT `FK_member_routine_schedule_routine` FOREIGN KEY (`member_routine_id`) REFERENCES `member_routine` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
