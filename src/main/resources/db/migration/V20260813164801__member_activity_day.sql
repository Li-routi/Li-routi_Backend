-- 회원이 "이날 루틴을 했다" 는 사실 하나.
--
-- 지금 전역 활동일 기록이 없다. 스트릭은 그룹·챌린지 단위로만 있어서 "며칠 했는가" 를 묻는
-- 조건(ACTIVE_DAYS·STREAK_DAYS)이 셀 대상 자체가 없었다.
--
-- ⚠️ 캐릭터 전용이 아니다. 업적·리포트처럼 "얼마나 꾸준히 썼는가" 를 묻는 곳이면 어디서나
--    쓴다. 캐릭터가 첫 소비자일 뿐이다. 그래서 표에 캐릭터를 가리키는 칸이 없다.

CREATE TABLE `member_activity_day`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `member_id`     BIGINT      NOT NULL,
    -- 루틴을 한 날(KST). 개인·그룹·챌린지 인증을 모두 포함한다 -- 해금은 "앱을 꾸준히
    -- 썼는가" 에 대한 보상이지 특정 기능을 밀어주는 장치가 아니다.
    `activity_date` DATE        NOT NULL,
    -- 그날 예정된 개인 루틴을 하나도 빠짐없이 완수했는가. 둥지 레벨이 이 값을 센다.
    --
    -- ⚠️ 행의 존재와 뜻이 다르다. 행은 "뭐라도 했다"(셋 합산)이고, 이 값은 개인 루틴만 보고
    --    "예정된 것을 전부 했다" 이다. 그룹·챌린지만 한 날은 행이 생기지만 이 값은 0 이다.
    `all_completed` TINYINT(1)  NOT NULL DEFAULT 0,
    `created_at`    DATETIME(6) NOT NULL,
    `updated_at`    DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    -- 하루 1일을 이 제약이 보장한다. 루틴 둘을 동시에 완료해도 두 번째 INSERT 가 튕기므로
    -- 잠금이 필요 없다.
    UNIQUE KEY `uk_member_activity_day` (`member_id`, `activity_date`),
    CONSTRAINT `fk_member_activity_day_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
