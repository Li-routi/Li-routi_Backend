-- 인증 통과 리워드의 "지금 유효한 지급" 상태.
--
-- 지급 이력 자체는 wallet_transaction 이 갖는다. 이 테이블과 원장은 역할이 다르다 —
-- 원장은 무슨 일이 있었나(불변), 이쪽은 지금 유효한 지급이 있는가(가변)다. member_wallet 과
-- wallet_transaction 을 나눈 것과 같은 구조다.
--
-- 합치면 재지급이 막힌다. 원장은 행을 지우지 않는 것이 원칙인데, 회수 뒤 다시 인증하면
-- 지급이 되살아나야 한다. 원장 하나로 두면 그 인증에 대한 멱등 키가 이미 존재해 재지급이
-- 조용히 건너뛰어지고, 사용자는 지웠다가 다시 올린 뒤로 리워드를 영영 못 받는다.

CREATE TABLE `reward_grant`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `member_id`    BIGINT      NOT NULL,
    -- 지금은 VERIFICATION 만 쓴다. 나중에 스트릭 마일스톤을 얹을 때 테이블을 다시 만들지
    -- 않으려고 컬럼으로 둔다.
    `reason`       VARCHAR(30) NOT NULL,
    `reference_id` BIGINT      NOT NULL,
    `currency`     VARCHAR(20) NOT NULL,
    `amount`       INT         NOT NULL,
    `created_at`   DATETIME(6) NOT NULL,
    `updated_at`   DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    -- 중복 지급의 최종 방어선이다. 따닥으로 두 번 들어와도 하나만 통과한다.
    -- 당일 재인증은 행을 새로 만들지 않고 덮어쓰므로 reference_id 가 같다 — 사진만 갈아끼우고
    -- 리워드를 또 받는 길이 여기서 막힌다.
    UNIQUE KEY `uk_reward_grant_reason_reference` (`member_id`, `reason`, `reference_id`),
    CONSTRAINT `fk_reward_grant_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `ck_reward_grant_positive` CHECK (`amount` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
