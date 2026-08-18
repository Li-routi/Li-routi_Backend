-- 구매를 1급 레코드로 둔다.
--
-- 지금은 여섯 개를 한 번에 사면 member_avatar_item 여섯 행과 wallet_transaction 한두 행으로
-- 흩어지고, 이 둘을 하나의 구매로 묶는 단서가 purchased_at 타임스탬프뿐이다. 같은 초에 두
-- 구매가 들어오면 그마저 무너진다.
--
-- 표를 두는 이유는 멱등성이 아니다. 멱등성만 필요하면 지갑 멱등 키에 장바구니 해시를 덧붙이는
-- 것으로 충분하다. 백오피스가 요구하는 것이 구매 id 이기 때문이다 — 구매 내역 조회, CS 문의
-- 대조, 환불·회수가 전부 "이 구매" 를 가리킬 수 있어야 한다.

CREATE TABLE `avatar_purchase`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`        BIGINT       NOT NULL,

    -- 클라이언트가 만든다. 요청 DTO 가 64자로 제한하므로 그 길이에 맞춘다.
    -- 지갑에는 "avatar:purchase:" 를 붙여 넘기는데(16자), 64 + 16 = 80 이라
    -- wallet_transaction.idempotency_key VARCHAR(150) 안에 들어간다.
    `idempotency_key`  VARCHAR(64)  NOT NULL,

    -- 장바구니를 가리키는 값. 정렬한 itemIds 를 쉼표로 이어 SHA-256 한 소문자 hex 다.
    --
    -- 이것이 없으면 키 재사용이 공짜 아이템이 된다. 지갑은 같은 키를 "이미 처리한 요청" 으로
    -- 보아 차감 없이 예전 결과를 돌려주는데, 그 전제는 "같은 요청" 이다. 장바구니가 달라졌는데
    -- 키가 같으면 전제가 깨져 보유 행만 생기고 값이 빠지지 않는다.
    --
    -- 길이가 늘 64 라 CHAR 가 맞아 보이지만 VARCHAR 로 둔다. 엔티티의 @Column(length = 64) 를
    -- Hibernate 가 varchar 로 읽으므로, CHAR 로 만들면 ddl-auto=validate 와 어긋날 수 있다.
    `item_fingerprint` VARCHAR(64)  NOT NULL,

    `purchased_at`     DATETIME(6)  NOT NULL,
    `created_at`       DATETIME(6)  NOT NULL,
    `updated_at`       DATETIME(6)  NOT NULL,

    PRIMARY KEY (`id`),

    -- 선점이 이 제약 위에서 이뤄진다. 같은 회원이 같은 키로 두 번 들어오면 두 번째가 튕기고,
    -- 그때 기존 행의 fingerprint 를 보고 같은 구매인지 다른 구매인지 가른다.
    UNIQUE KEY `uk_avatar_purchase_idempotency` (`member_id`, `idempotency_key`),

    -- 백오피스의 "이 회원의 구매 내역" 을 최근순으로 읽는다.
    KEY `idx_avatar_purchase_member_purchased` (`member_id`, `purchased_at`),

    CONSTRAINT `fk_avatar_purchase_member`
        FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 어느 구매에서 온 아이템인가.
--
-- NULL 을 허용하는 것은 이 표가 생기기 전에 산 행이 있기 때문이다. 그 행들은 백필하지 않고
-- NULL 로 둔다 — 묶을 근거가 purchased_at 뿐인데, 그것으로 구매를 가를 수 없다는 것이 위 표를
-- 만드는 이유다. 같은 초에 두 구매가 들어왔으면 잘못된 구매에 아이템이 붙는다.
--
-- 새로 만드는 행은 애플리케이션이 반드시 채운다. 제약이 못 지키는 만큼 구매 경로가
-- ShopCommandService.purchase 한 곳뿐이라는 사실에 기댄다.
ALTER TABLE `member_avatar_item`
    ADD COLUMN `avatar_purchase_id` BIGINT NULL AFTER `avatar_item_id`,
    ADD KEY `idx_member_avatar_item_purchase` (`avatar_purchase_id`),
    ADD CONSTRAINT `fk_member_avatar_item_purchase`
        FOREIGN KEY (`avatar_purchase_id`) REFERENCES `avatar_purchase` (`id`);
