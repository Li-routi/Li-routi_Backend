-- 앱을 켰을 때 반드시 한 번 보여줘야 하는 팝업.
--
-- ⚠️ 알림함(notification)과 다른 것이다. 알림함은 목록이고 나중에 봐도 되지만, 이것은 즉시
--    1회이고 사용자가 봤다는 확인(ack)이 있어야 사라진다. 푸시도 여기 관여하지 않는다 --
--    앱 밖으로 알리는 일은 알림 도메인이 하고, 발행하는 쪽이 둘을 함께 부른다.
--
-- 이 표는 도메인을 모른다. character_id 같은 참조를 두면 조회할 때 이 모듈이 캐릭터·업적을
-- 전부 알아야 하고, 소비자가 늘 때마다 모듈을 고쳐야 해 모듈화한 의미가 없다. 그래서
-- 발행하는 쪽이 보여줄 내용을 채워 넣고 모듈은 그대로 돌려준다.

CREATE TABLE `pending_popup`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `member_id`      BIGINT       NOT NULL,
    -- CHARACTER_UNLOCKED · ACHIEVEMENT_ACHIEVED … 자바 enum 이 아니라 문자열이다.
    -- 소비자가 늘 때 이 표는 바뀌지 않는다.
    `popup_type`     VARCHAR(30)  NOT NULL,
    -- 문구를 복사해 둔다. 마스터가 바뀌어도 옛 팝업은 옛 문구로 뜨지만, 팝업은 곧 뜨고
    -- 사라지므로 실질적으로 문제가 되지 않는다. 결합을 끊는 값이 더 크다.
    `title`          VARCHAR(100) NOT NULL,
    `body`           VARCHAR(255) NOT NULL,
    -- 절대 URL 이 아니라 S3 key 다. 조회에서 조립한다.
    `image_key`      VARCHAR(512) NULL,
    -- 눌렀을 때 어디로 보낼지. 모듈은 이 값을 해석하지 않고 그대로 내려준다.
    `reference_type` VARCHAR(30)  NULL,
    `reference_id`   BIGINT       NULL,
    -- 같은 사건이면 몇 번을 다시 계산해도 같은 문자열이 나와야 한다. 요청 id 나 UUID 를
    -- 넣으면 재시도마다 달라져 아래 유니크가 아무것도 막지 못한다.
    `dedup_key`      VARCHAR(100) NOT NULL,
    -- NULL 이면 아직 안 보여줬다. 조회는 이 값이 비어 있는 것만 돌려준다.
    `acked_at`       DATETIME(6)  NULL,
    `created_at`     DATETIME(6)  NOT NULL,
    `updated_at`     DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    -- 중복 발행의 최종 방어선이다. 판정이 두 번 돌아도 두 번째 INSERT 가 튕긴다.
    -- member_id 가 선두라 키 안에 회원을 다시 넣지 않는다.
    UNIQUE KEY `uk_pending_popup_member_dedup` (`member_id`, `dedup_key`),
    -- 조회가 "이 회원의 안 본 것" 하나뿐이라 그 모양 그대로 인덱스를 둔다. created_at 까지
    -- 넣어 정렬이 인덱스를 벗어나지 않게 한다.
    KEY `idx_pending_popup_member_acked` (`member_id`, `acked_at`, `created_at`),
    CONSTRAINT `fk_pending_popup_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
