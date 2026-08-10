-- 아바타 아이템 상점. 표 셋이다.
--
--   avatar_item               파는 것 (마스터)
--   member_avatar_item        가진 것 (영구 보유)
--   member_avatar_equipment   입은 것 (슬롯당 하나)
--
-- 기존 product·member_order 를 쓰지 않는 이유는 그쪽이 실물 배송 쇼핑이기 때문이다.
-- 배송지도 소모품 분류도 없고 결제 수단이 현금이 아니라 재화다.

CREATE TABLE `avatar_item`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    -- HEAD · FACE · BODY · HAND. 넷은 서로 겹치지 않으므로 "무엇을 입으면 무엇이 벗겨지는가"
    -- 를 정의할 필요가 없다. 화면의 "전체" 탭은 분류가 아니라 필터 없음이라 값으로 두지 않는다.
    `slot`       VARCHAR(30)  NOT NULL,
    `currency`   VARCHAR(20)  NOT NULL,
    `price`      INT          NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    `image_url`  VARCHAR(2048) NOT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    -- 마스터라 소프트 삭제를 두지 않는다. 판매를 내릴 때는 이 값을 내린다 — 이미 산 사람의
    -- 보유 행이 가리키는 대상이 사라지면 안 된다.
    `active`     TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at` DATETIME(6)  NOT NULL,
    `updated_at` DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    -- 기본 키가 있어 중복 방지에는 필요 없다. member_avatar_equipment 가
    -- (avatar_item_id, slot) 복합 FK 로 참조할 대상이라 둔다.
    UNIQUE KEY `uk_avatar_item_id_slot` (`id`, `slot`),
    -- 기본 제공 아이템을 두지 않으므로 0 짜리가 존재할 이유가 없다. 허용하면 무료 아이템을
    -- 구매로 볼지 자동 보유로 볼지가 구현마다 갈린다.
    CONSTRAINT `ck_avatar_item_price_positive` CHECK (`price` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `member_avatar_item`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `member_id`      BIGINT      NOT NULL,
    `avatar_item_id` BIGINT      NOT NULL,
    -- 구매 시점 스냅샷. 운영이 가격이나 결제 재화를 바꿔도 "얼마에 샀나" 가 따라 움직이면
    -- 안 된다.
    `currency`       VARCHAR(20) NOT NULL,
    `paid_price`     INT         NOT NULL,
    `purchased_at`   DATETIME(6) NOT NULL,
    `created_at`     DATETIME(6) NOT NULL,
    `updated_at`     DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    -- 중복 구매의 최종 방어선이다. 애플리케이션 분기로만 막으면 따닥으로 들어온 두 요청이
    -- 모두 통과하고, 여기서는 재화가 두 번 빠진다.
    UNIQUE KEY `uk_member_avatar_item_member_item` (`member_id`, `avatar_item_id`),
    CONSTRAINT `fk_member_avatar_item_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_member_avatar_item_item` FOREIGN KEY (`avatar_item_id`) REFERENCES `avatar_item` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `member_avatar_equipment`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `member_id`      BIGINT      NOT NULL,
    -- 아이템에도 있는 값이다. 여기 두지 않으면 "한 슬롯에 하나" 를 유니크로 강제할 수 없다 —
    -- 조인해야 알 수 있는 값에는 유니크를 걸 수 없다. 대신 아래 복합 FK 로 어긋날 수 없게 묶는다.
    `slot`           VARCHAR(30) NOT NULL,
    `avatar_item_id` BIGINT      NOT NULL,
    `created_at`     DATETIME(6) NOT NULL,
    `updated_at`     DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    -- 벗은 슬롯은 행이 없다. NULL 을 넣지 않는다 — "안 입었다" 와 "입었는데 값이 비었다" 가
    -- 구분되지 않는다.
    UNIQUE KEY `uk_member_avatar_equipment_member_slot` (`member_id`, `slot`),
    CONSTRAINT `fk_member_avatar_equipment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    -- 아이템 id 만 참조하면 두 slot 이 서로 다른 값이 되어도 DB 는 모른다. 복합으로 묶으면
    -- 어긋난 조합을 넣는 것 자체가 실패하고, 이미 착용된 아이템의 슬롯을 마스터에서 바꾸는
    -- 것도 막힌다.
    CONSTRAINT `fk_member_avatar_equipment_item_slot`
        FOREIGN KEY (`avatar_item_id`, `slot`) REFERENCES `avatar_item` (`id`, `slot`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
