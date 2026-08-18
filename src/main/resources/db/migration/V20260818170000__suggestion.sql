-- 마이페이지 건의.
--
--   suggestion_category   등록할 때 고르는 분류. R__ 시드가 단일 진실 공급원
--   suggestion            건의 본문과 상태

-- 분류를 자바 enum 이나 MySQL ENUM 이 아니라 표로 둔다.
--
-- 분류를 하나 더하는 일이 배포 없이 되어야 하고, 나중에 백오피스가 이 목록을 관리한다.
-- 챌린지 카테고리가 실제 MySQL ENUM 이라 값 하나 더하는 데 ALTER TABLE 이 필요했고,
-- 기존 값의 순서를 바꾸면 저장된 행의 뜻이 통째로 어긋나는 함정도 있었다.
CREATE TABLE `suggestion_category`
(
    `id`            BIGINT      NOT NULL,
    `code`          VARCHAR(30) NOT NULL,
    `name`          VARCHAR(30) NOT NULL,
    `display_order` INT         NOT NULL,
    `active`        TINYINT(1)  NOT NULL DEFAULT 1,
    `created_at`    DATETIME(6) NOT NULL,
    `updated_at`    DATETIME(6) NOT NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_suggestion_category_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `suggestion`
(
    `id`                     BIGINT        NOT NULL AUTO_INCREMENT,
    `member_id`              BIGINT        NOT NULL,
    `suggestion_category_id` BIGINT        NOT NULL,

    -- TEXT 가 아닌 이유는 상한이 있어야 요청 DTO 가 그것을 검증할 수 있기 때문이다.
    -- 비정상적으로 큰 본문을 DB 앞에서 막는다.
    `content`                VARCHAR(2000) NOT NULL,

    -- 허용값은 자바 enum 이 정한다(RECEIVED · IN_PROGRESS · DONE).
    --
    -- 지금은 바꾸는 경로가 없어 전부 RECEIVED 에 머문다. 그럼에도 컬럼을 지금 두는 것은,
    -- 나중에 더하면 이미 쌓인 건의 전부에 기본값을 소급 부여해야 하고 그 시점에는 무엇이
    -- 처리됐는지 알 방법이 없기 때문이다.
    `status`                 VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED',

    `created_at`             DATETIME(6)   NOT NULL,
    `updated_at`             DATETIME(6)   NOT NULL,

    PRIMARY KEY (`id`),

    -- 내 건의 목록이 id DESC 로 커서를 넘긴다. created_at 을 쓰지 않는 것은 같은 초에 둘이
    -- 들어오면 순서가 흔들리기 때문이다.
    KEY `idx_suggestion_member_id` (`member_id`, `id` DESC),

    CONSTRAINT `fk_suggestion_member`
        FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_suggestion_category`
        FOREIGN KEY (`suggestion_category_id`) REFERENCES `suggestion_category` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
