-- 아바타 아이템 마스터 시드.
--
-- 내용이 바뀌면 다시 도는 파일이라 매번 실행된다. 그래서 고정 id + upsert 로 멱등하게 쓴다.
--
-- ⚠️ 실제 자산(이름·이미지·가격)이 아직 없다. 슬롯당 둘씩 최소한만 넣어 API 와 화면이 붙는
--    것을 확인하는 용도이고, 자산이 나오면 이 파일을 갈아 끼운다. 이미 산 사람이 있을 수
--    있으므로 id 는 유지하고 내용만 바꾼다 — id 를 바꾸면 남의 보유가 다른 아이템을 가리킨다.
--
-- ⚠️ id 3·4 는 비어 있다. 얼굴 슬롯을 걷어내며 지운 자리이고(V20260813091352), 그 번호를
--    다른 아이템에 다시 쓰지 않는다. 재사용하면 지워진 아이템을 참조하던 기록이 있는 환경에서
--    엉뚱한 물건을 가리키게 된다. 남은 번호는 그대로 두고 새 아이템은 9 부터 붙인다.
--
-- ⚠️ slot 은 갱신하지 않는다. member_avatar_equipment 의 복합 외래 키 (avatar_item_id, slot)
--    가 이미 착용된 아이템의 슬롯 변경을 막는데, 이 파일은 매 배포마다 돌기 때문에 여기서
--    slot 을 바꾸면 그 순간 외래 키 오류로 부팅이 멈춘다. 자리를 옮겨야 하면 새 아이템을
--    만들고 옛것을 active = 0 으로 내린다 — 파는 물건의 자리가 바뀌는 것은 사실상 다른
--    물건이다.
--
-- 가격 재화는 둘을 섞어 둔다. GEM 은 현금으로만 얻는 유료 재화, TOPAZ 는 챌린지로 버는 무료
-- 재화다. 무료 사용자도 살 수 있는 아이템이 있어야 화면이 성립한다.

INSERT INTO `avatar_item` (`id`, `slot`, `currency`, `price`, `name`, `image_url`, `sort_order`,
                           `active`, `created_at`, `updated_at`)
VALUES (1, 'HEAD', 'TOPAZ', 300, '기본 모자', 'https://placehold.co/240x240?text=HEAD1', 1, 1, NOW(6), NOW(6)),
       (2, 'HEAD', 'GEM', 600, '밀짚모자', 'https://placehold.co/240x240?text=HEAD2', 2, 1, NOW(6), NOW(6)),
       (5, 'BODY', 'TOPAZ', 500, '줄무늬 티셔츠', 'https://placehold.co/240x240?text=BODY1', 1, 1, NOW(6), NOW(6)),
       (6, 'BODY', 'GEM', 900, '후드 집업', 'https://placehold.co/240x240?text=BODY2', 2, 1, NOW(6), NOW(6)),
       (7, 'HAND', 'TOPAZ', 300, '텀블러', 'https://placehold.co/240x240?text=HAND1', 1, 1, NOW(6), NOW(6)),
       (8, 'HAND', 'GEM', 600, '반려 식물', 'https://placehold.co/240x240?text=HAND2', 2, 1, NOW(6), NOW(6)) AS new_row
ON DUPLICATE KEY UPDATE `currency`   = new_row.`currency`,
                        `price`      = new_row.`price`,
                        `name`       = new_row.`name`,
                        `image_url`  = new_row.`image_url`,
                        `sort_order` = new_row.`sort_order`,
                        `active`     = new_row.`active`,
                        `updated_at` = NOW(6);
