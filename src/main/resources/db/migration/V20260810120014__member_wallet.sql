-- 재화 지갑. 잔액(member_wallet)과 거래 원장(wallet_transaction)이다.
--
-- 지금까지 이 서비스에는 재화를 담을 곳이 아예 없었다. 챌린지의 reward 는 "지급할 수량"을
-- 보관만 했고, 리포트의 획득 코인은 하드코딩 0 이었다. 상점(아이템 구매·충전)과 인증
-- 리워드가 전부 이 두 테이블 위에서 돈다.

CREATE TABLE `member_wallet`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`    BIGINT       NOT NULL,
    `currency`     VARCHAR(20)  NOT NULL,
    -- 유상(현금으로 산 것)과 무상(보너스·리워드)을 나눠 둔다. 합쳐 두면 나중에 못 쪼갠다 --
    -- 미사용 유상 재화는 청약철회 대상이라 "환불 가능한 금액"을 계산할 수 있어야 하고,
    -- 무상에만 유효기간을 두는 정책도 흔하다. 소급 판정할 정보가 애초에 남지 않는 것이 문제다.
    `paid_balance` INT          NOT NULL DEFAULT 0,
    `free_balance` INT          NOT NULL DEFAULT 0,
    `created_at`   DATETIME(6)  NOT NULL,
    `updated_at`   DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    -- 한 회원의 한 재화에 지갑은 하나다. 동시에 두 요청이 지갑을 만들려 하면 하나만 통과한다.
    UNIQUE KEY `uk_member_wallet_member_currency` (`member_id`, `currency`),
    CONSTRAINT `fk_member_wallet_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 잔액이 움직인 모든 기록. 이 테이블이 진실이고 member_wallet 은 그것을 더한 결과다.
CREATE TABLE `wallet_transaction`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`           BIGINT       NOT NULL,
    `currency`            VARCHAR(20)  NOT NULL,
    `transaction_type`    VARCHAR(30)  NOT NULL,
    -- 증감이라 차감이면 음수다. 유상·무상을 따로 적어야 어느 쪽이 줄었는지 남는다.
    `paid_delta`          INT          NOT NULL,
    `free_delta`          INT          NOT NULL,
    -- 거래 직후 잔액. 원장을 처음부터 더한 값과 이 값이 어긋나면 그 지점이 사고 지점이다.
    `paid_balance_after`  INT          NOT NULL,
    `free_balance_after`  INT          NOT NULL,
    -- 멱등 키. 네트워크가 끊겨 클라이언트가 재시도하는 것은 정상 동작이고, 그때 두 번
    -- 차감되거나 두 번 지급되면 그대로 돈 문제가 된다. 나중에 붙이면 그 사이 거래는
    -- 소급해서 보호되지 않으므로 처음부터 NOT NULL 로 둔다.
    `idempotency_key`     VARCHAR(150) NOT NULL,
    `reference_type`      VARCHAR(30)  NULL,
    `reference_id`        BIGINT       NULL,
    `created_at`          DATETIME(6)  NOT NULL,
    `updated_at`          DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wallet_transaction_idempotency` (`idempotency_key`),
    -- 거래 내역 조회용. id 를 뒤에 붙여 최신순 정렬까지 덮는다.
    KEY `idx_wallet_transaction_member` (`member_id`, `id`),
    CONSTRAINT `fk_wallet_transaction_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
