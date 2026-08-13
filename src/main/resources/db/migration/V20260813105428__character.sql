-- 캐릭터 도메인. 표 넷이다.
--
--   avatar_character            마스터 (앱이 제공하는 캐릭터)
--   character_unlock_condition  해금 조건 (여러 행이면 AND)
--   member_character            보유 (행이 있으면 해금, 없으면 알)
--   member_selected_character   선택 (지금 쓰고 있는 캐릭터)
--
-- ⚠️ 마스터 표 이름이 character 가 아니라 avatar_character 다. character 는 MySQL 예약어라
--    백틱 없이는 CREATE 도 SELECT 도 실패한다. 백틱으로 감쌀 수는 있지만 시드·네이티브 쿼리
--    어디서든 한 번만 빠뜨리면 터지고, 자바에서도 클래스 이름이 java.lang.Character 와 겹친다.
--    설계 문서의 이름을 이 파일 기준으로 맞췄다.

CREATE TABLE `avatar_character`
(
    `id`              BIGINT      NOT NULL,
    -- 시드가 직접 지정한다. 자동 증가를 쓰지 않는 이유는 조건·자산이 이 값을 가리키기
    -- 때문이다 -- 환경마다 번호가 달라지면 시드가 재현되지 않는다.
    `code`            VARCHAR(30) NOT NULL,
    `name`            VARCHAR(30) NOT NULL,
    -- 잠긴 상태로 보여줄 알 그림. 해금 뒤에는 쓰지 않는다 -- 목록의 잠금 표시 전용이다.
    `egg_image_key`   VARCHAR(512) NOT NULL,
    `adult_image_key` VARCHAR(512) NOT NULL,
    -- 절대 URL 이 아니라 S3 key 다. 도메인이 바뀌면 행을 전부 고쳐야 하고 CDN 을 붙이는
    -- 순간 그 값이 낡는다. 조회에서 resolveViewUrl 로 조립한다.
    `hidden`          TINYINT(1)  NOT NULL DEFAULT 0,
    `display_order`   INT         NOT NULL DEFAULT 0,
    `active`          TINYINT(1)  NOT NULL DEFAULT 1,
    `created_at`      DATETIME(6) NOT NULL,
    `updated_at`      DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    -- 시드와 조건이 code 로 서로를 가리킨다. id 는 숫자라 무엇을 뜻하는지 읽히지 않는다.
    UNIQUE KEY `uk_avatar_character_code` (`code`),
    -- member_selected_character 가 (member_id, character_id) 복합 FK 로 보유를 참조하므로
    -- 여기서는 필요 없지만, 목록 정렬이 항상 이 순서라 인덱스를 둔다.
    KEY `idx_avatar_character_active_order` (`active`, `display_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `character_unlock_condition`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `character_id`    BIGINT       NOT NULL,
    -- 판정기 종류. 자바 enum 이 아니라 문자열로 둔다 -- 백오피스에서 조건을 걸어 캐릭터를
    -- 추가하려는 요구가 있어, 종류가 늘 때 배포 없이 데이터만 넣을 수 있어야 한다.
    `condition_key`   VARCHAR(30)  NOT NULL,
    -- 키마다 뜻이 다르다. 카테고리 조건이면 논리 키(쉼표로 나열하면 합집합)가 들어간다.
    -- 카테고리 id 를 넣지 않는다 -- 개인·그룹·챌린지가 세 체계로 갈라져 id 가 서로 다르다.
    `condition_param` VARCHAR(255) NULL,
    `target_count`    INT          NOT NULL,
    `sort_order`      INT          NOT NULL DEFAULT 0,
    `created_at`      DATETIME(6)  NOT NULL,
    `updated_at`      DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_character_unlock_condition_character` (`character_id`),
    CONSTRAINT `fk_character_unlock_condition_character`
        FOREIGN KEY (`character_id`) REFERENCES `avatar_character` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `member_character`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `member_id`     BIGINT      NOT NULL,
    `character_id`  BIGINT      NOT NULL,
    `unlocked_date` DATE        NOT NULL,
    `created_at`    DATETIME(6) NOT NULL,
    `updated_at`    DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    -- 해금의 멱등을 이 제약이 보장한다. 판정이 두 번 돌아도 두 번째 INSERT 가 튕기고,
    -- 성공한 요청만 팝업을 발행한다. 제약 위반은 "졌다" 는 뜻이라 조용히 끝낸다.
    --
    -- member_selected_character 의 복합 외래 키가 참조할 대상이기도 하다.
    UNIQUE KEY `uk_member_character` (`member_id`, `character_id`),
    CONSTRAINT `fk_member_character_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_member_character_character`
        FOREIGN KEY (`character_id`) REFERENCES `avatar_character` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `member_selected_character`
(
    -- 회원 하나에 선택 하나. 기본 키가 그것을 공짜로 보장한다.
    -- member_character 에 플래그를 두면 MySQL 에 부분 유니크가 없어 "회원당 하나" 를
    -- 제약으로 만들 수 없고, 따닥으로 들어온 두 요청이 둘 다 통과하면 선택이 둘이 된다.
    `member_id`    BIGINT      NOT NULL,
    `character_id` BIGINT      NOT NULL,
    `created_at`   DATETIME(6) NOT NULL,
    `updated_at`   DATETIME(6) NOT NULL,
    PRIMARY KEY (`member_id`),
    -- 보유하지 않은 캐릭터를 고르는 것 자체를 실패시킨다 -- 알 상태는 선택할 수 없다는
    -- 규칙이 애플리케이션 검사가 아니라 제약이 된다. 착용이 (avatar_item_id, slot) 복합
    -- FK 로 어긋난 조합을 막는 것과 같은 방식이다.
    CONSTRAINT `fk_member_selected_character_owned`
        FOREIGN KEY (`member_id`, `character_id`)
            REFERENCES `member_character` (`member_id`, `character_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
