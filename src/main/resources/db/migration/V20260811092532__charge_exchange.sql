-- 재화 충전·교환. 표 셋이다.
--
--   charge_product     현금으로 사는 것
--   exchange_product   재화로 사는 것
--   charge_payment     결제 한 건
--
-- 상품 마스터를 둘로 나눈 이유는 한 표에 담으면 price_krw 는 충전에만, from/to 는 교환에만
-- 의미가 있어 어느 컬럼이 언제 유효한지 매번 따져야 하기 때문이다. product·member_order 를
-- 재사용하지 않기로 한 이유가 그것인데 같은 표를 새로 만들 이유가 없다.

CREATE TABLE `charge_product`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    -- 유료 재화만 현금으로 판다. 무료 재화를 파는 상품이 생기면 결제는 성공하는데 지급에서
    -- 거부된다(무료 재화는 유상 잔액을 가질 수 없다) — 돈은 나갔고 재화는 없는 상태가 된다.
    `reward_currency` VARCHAR(20) NOT NULL,
    `reward_amount`   INT         NOT NULL,
    `bonus_amount`    INT         NOT NULL DEFAULT 0,
    `price_krw`       INT         NOT NULL,
    `popular`         TINYINT(1)  NOT NULL DEFAULT 0,
    `sort_order`      INT         NOT NULL DEFAULT 0,
    `active`          TINYINT(1)  NOT NULL DEFAULT 1,
    `created_at`      DATETIME(6) NOT NULL,
    `updated_at`      DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `ck_charge_product_currency` CHECK (`reward_currency` = 'GEM'),
    CONSTRAINT `ck_charge_product_amount` CHECK (`reward_amount` >= 0 AND `bonus_amount` >= 0
                                                 AND `reward_amount` + `bonus_amount` > 0),
    CONSTRAINT `ck_charge_product_price` CHECK (`price_krw` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `exchange_product`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `from_currency` VARCHAR(20) NOT NULL,
    `from_amount`   INT         NOT NULL,
    `to_currency`   VARCHAR(20) NOT NULL,
    `to_amount`     INT         NOT NULL,
    `sort_order`    INT         NOT NULL DEFAULT 0,
    `active`        TINYINT(1)  NOT NULL DEFAULT 1,
    `created_at`    DATETIME(6) NOT NULL,
    `updated_at`    DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    -- 같은 재화끼리 바꾸는 것은 뜻이 없다.
    CONSTRAINT `ck_exchange_product_currency` CHECK (`from_currency` <> `to_currency`),
    CONSTRAINT `ck_exchange_product_amount` CHECK (`from_amount` > 0 AND `to_amount` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `charge_payment`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `member_id`         BIGINT      NOT NULL,
    `charge_product_id` BIGINT      NOT NULL,
    -- 포트원 V2 를 쓴다. payment_id 는 서버가 결제 전에 만들고, tx_id 는 포트원이 결제 뒤에
    -- 알려준다. V1(merchant_uid / imp_uid)과 섞어 쓰지 않는다.
    `payment_id`        VARCHAR(64) NOT NULL,
    `tx_id`             VARCHAR(64) NULL,
    `status`            VARCHAR(20) NOT NULL,
    -- 결제 시작 시점에 기록한다. 검증에서 이 값과 정확히 대조한다 — 없으면 클라이언트가 보낸
    -- 금액을 믿는 수밖에 없고, 그러면 100원 결제하고 11만원어치를 받는 길이 열린다.
    `expected_amount`   INT         NOT NULL,
    -- 지급 내용을 결제 시점에 복사한다. 상품 id 만 두고 지급 때 다시 읽으면, 결제 도중 운영이
    -- 상품을 고치는 순간 금액 검증은 통과하는데 지급 수량이 달라진다.
    `reward_currency`   VARCHAR(20) NOT NULL,
    `reward_amount`     INT         NOT NULL,
    `bonus_amount`      INT         NOT NULL,
    `paid_amount`       INT         NULL,
    `requested_at`      DATETIME(6) NOT NULL,
    `paid_at`           DATETIME(6) NULL,
    `failed_at`         DATETIME(6) NULL,
    `fail_reason`       VARCHAR(255) NULL,
    `created_at`        DATETIME(6) NOT NULL,
    `updated_at`        DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_charge_payment_payment_id` (`payment_id`),
    -- 같은 거래로 두 번 지급되면 그대로 손해다. 애플리케이션 분기로만 막으면 뚫린다.
    UNIQUE KEY `uk_charge_payment_tx_id` (`tx_id`),
    CONSTRAINT `fk_charge_payment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_charge_payment_product` FOREIGN KEY (`charge_product_id`) REFERENCES `charge_product` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
