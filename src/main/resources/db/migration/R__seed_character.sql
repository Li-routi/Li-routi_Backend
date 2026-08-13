-- 캐릭터 마스터 시드. 열셋이다.
--
-- 내용이 바뀌면 다시 도는 파일이라 매번 실행된다. 그래서 고정 id + upsert 로 멱등하게 쓴다.
--
-- id 는 1~999 대역을 쓴다. 1000~ 은 백오피스가 나중에 추가할 몫으로 비워 둔다.
-- id 를 바꾸면 남의 보유(member_character)가 다른 캐릭터를 가리키므로 절대 재사용하지 않는다.
--
-- 이미지 key 는 실제로 S3 에 올라가 있는 값이다(avatar/character/{code}/...). 절대 URL 이
-- 아니라 key 이며, 꼬리의 -v1 이 교체 규칙을 강제한다 -- 같은 이름으로 덮어쓰면 앱과 CDN 이
-- 옛 그림을 계속 보여주므로, 자산을 바꿀 때는 -v2 로 올리고 이 파일이 그것을 가리키게 한다.
--
-- 등급 컬럼은 두지 않는다. 기획에 등급 넷(기본·레어·에픽·특별)이 언급돼 있으나 화면에서
-- 쓰는 곳이 정해지지 않았고, 캐릭터를 파는 것이 아니라 조건으로 여는 것이라 난이도는 이미
-- 해금 조건이 표현한다. 필요해지면 그때 컬럼 하나를 더한다.
--
-- 해금 조건은 R__seed_character_unlock_condition.sql 에 따로 있다. 루티(1)만 조건 행이
-- 없는데, 그것이 "가입 시 기본 지급" 을 표현하는 방식이다 — 조건이 하나도 없으면 항상
-- 열려 있는 기본 캐릭터라는 뜻이다.
--
-- ⚠️ 그래서 조건 시드를 비워 두면 열셋이 전부 기본 캐릭터가 된다. 판정을 붙이기 전에 조건이
--    먼저 들어가 있어야 한다.

INSERT INTO `avatar_character` (`id`, `code`, `name`, `egg_image_key`, `adult_image_key`,
                                `hidden`, `display_order`, `active`, `created_at`, `updated_at`)
VALUES (1, 'ROUTI', '루티',
        'avatar/character/ROUTI/egg-v1.png', 'avatar/character/ROUTI/adult-v1.png',
        0, 1, 1, NOW(6), NOW(6)),
       (2, 'NOA', '노아',
        'avatar/character/NOA/egg-v1.png', 'avatar/character/NOA/adult-v1.png',
        0, 2, 1, NOW(6), NOW(6)),
       (3, 'MORI', '모리',
        'avatar/character/MORI/egg-v1.png', 'avatar/character/MORI/adult-v1.png',
        0, 3, 1, NOW(6), NOW(6)),
       (4, 'KOKO', '코코',
        'avatar/character/KOKO/egg-v1.png', 'avatar/character/KOKO/adult-v1.png',
        0, 4, 1, NOW(6), NOW(6)),
       (5, 'YUKI', '유키',
        'avatar/character/YUKI/egg-v1.png', 'avatar/character/YUKI/adult-v1.png',
        0, 5, 1, NOW(6), NOW(6)),
       (6, 'MINT', '민트',
        'avatar/character/MINT/egg-v1.png', 'avatar/character/MINT/adult-v1.png',
        0, 6, 1, NOW(6), NOW(6)),
       (7, 'SOLA', '솔라',
        'avatar/character/SOLA/egg-v1.png', 'avatar/character/SOLA/adult-v1.png',
        0, 7, 1, NOW(6), NOW(6)),
       (8, 'PADO', '파도',
        'avatar/character/PADO/egg-v1.png', 'avatar/character/PADO/adult-v1.png',
        0, 8, 1, NOW(6), NOW(6)),
       (9, 'KKARU', '까루',
        'avatar/character/KKARU/egg-v1.png', 'avatar/character/KKARU/adult-v1.png',
        0, 9, 1, NOW(6), NOW(6)),
       (10, 'DONGGEUL', '동글',
        'avatar/character/DONGGEUL/egg-v1.png', 'avatar/character/DONGGEUL/adult-v1.png',
        0, 10, 1, NOW(6), NOW(6)),
       (11, 'PPIA', '삐아',
        'avatar/character/PPIA/egg-v1.png', 'avatar/character/PPIA/adult-v1.png',
        0, 11, 1, NOW(6), NOW(6)),
       (12, 'HORONG', '호롱',
        'avatar/character/HORONG/egg-v1.png', 'avatar/character/HORONG/adult-v1.png',
        0, 12, 1, NOW(6), NOW(6)),
       (13, 'DAMI', '다미',
        'avatar/character/DAMI/egg-v1.png', 'avatar/character/DAMI/adult-v1.png',
        0, 13, 1, NOW(6), NOW(6)) AS new_row
ON DUPLICATE KEY UPDATE
    -- ⚠️ 이 절은 어느 키가 충돌했는지 가리지 않는다. code 에도 유니크가 걸려 있어서, 시드의
    --    code 를 다른 id 가 이미 쓰고 있으면(예: 백오피스가 넣은 1000 번) 그 행의 이름과
    --    이미지가 시드 값으로 조용히 덮어써진다. id 대역 규칙과 "id 는 안정적이다" 는 계약이
    --    거기서 깨진다.
    --
    --    그래서 id 로 걸린 것이 아니면 id 를 NULL 로 만들어 즉시 실패시킨다. NOT NULL 컬럼이라
    --    "Column 'id' cannot be null" 로 마이그레이션이 멈춘다 — 조용히 남의 행을 고치는 것보다
    --    부팅이 실패하는 편이 낫다. 사람이 code 충돌을 풀고 다시 배포해야 한다.
                        `id`              = IF(`avatar_character`.`id` = new_row.`id`,
                                               `avatar_character`.`id`, NULL),
                        `code`            = new_row.`code`,
                        `name`            = new_row.`name`,
                        `egg_image_key`   = new_row.`egg_image_key`,
                        `adult_image_key` = new_row.`adult_image_key`,
                        `hidden`          = new_row.`hidden`,
                        `display_order`   = new_row.`display_order`,
                        `active`          = new_row.`active`,
                        `updated_at`      = NOW(6);
