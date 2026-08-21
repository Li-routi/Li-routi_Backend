-- 아바타 아이템 마스터 시드.
--
-- 내용이 바뀌면 다시 도는 파일이라 매번 실행된다. 그래서 고정 id + upsert 로 멱등하게 쓴다.
--
-- ⚠️ id 는 유지하고 내용만 바꾼다. id 를 바꾸면 남의 보유가 다른 아이템을 가리킨다. 아래
--    여섯(1·2·5·6·7·8)은 placehold.co 더미였던 자리이고, 슬롯을 지킨 채 내용만 실물로 바꿨다.
--
-- ⚠️ id 3·4 는 비어 있다. 얼굴 슬롯을 걷어내며 지운 자리이고(V20260813091352), 그 번호를
--    다른 아이템에 다시 쓰지 않는다. 재사용하면 지워진 아이템을 참조하던 기록이 있는 환경에서
--    엉뚱한 물건을 가리키게 된다. 새 아이템은 9 부터 붙인다.
--
-- ⚠️ slot 은 갱신하지 않는다. member_avatar_equipment 의 복합 외래 키 (avatar_item_id, slot)
--    가 이미 착용된 아이템의 슬롯 변경을 막는데, 이 파일은 매 배포마다 돌기 때문에 여기서
--    slot 을 바꾸면 그 순간 외래 키 오류로 부팅이 멈춘다. 자리를 옮겨야 하면 새 아이템을
--    만들고 옛것을 active = 0 으로 내린다 — 파는 물건의 자리가 바뀌는 것은 사실상 다른
--    물건이다.
--
-- image_key 는 절대 URL 이 아니라 S3 key 다. 꼬리의 -v1 이 교체 규칙을 강제한다 — 같은
-- 이름으로 덮어쓰면 앱과 CDN 이 옛 그림을 계속 보여주므로, 자산을 바꿀 때는 -v2 로 올리고
-- 이 파일이 그것을 가리키게 한다.
--
-- 가격 재화는 둘을 섞어 둔다. GEM 은 현금으로만 얻는 유료 재화, TOPAZ 는 챌린지로 버는 무료
-- 재화다. 무료 사용자도 살 수 있는 아이템이 있어야 화면이 성립한다.
--
-- 폼폼(응원)은 여기 없다. 자산 파일명이 "업적보상" 이고 achievement 의 limited_outfit_yn 이
-- 가리키는 한정 의상으로 보인다 — 돈으로 팔면 업적 보상의 뜻이 사라지므로 업적 지급이 붙을
-- 때 따로 넣는다.

INSERT INTO `avatar_item` (`id`, `slot`, `currency`, `price`, `name`, `image_key`, `sort_order`,
                           `active`, `created_at`, `updated_at`)
VALUES
-- 머리 — 수면안대 셋(유료) + 리본 넷(무료)
(1, 'HEAD', 'GEM', 200, '파란 수면안대', 'avatar/item/head/sleep-mask-blue-v1.png', 1, 1, NOW(6), NOW(6)),
(2, 'HEAD', 'GEM', 200, '보라 수면안대', 'avatar/item/head/sleep-mask-purple-v1.png', 2, 1, NOW(6), NOW(6)),
(9, 'HEAD', 'GEM', 200, '고양이 수면안대', 'avatar/item/head/sleep-mask-cat-v1.png', 3, 1, NOW(6), NOW(6)),
(10, 'HEAD', 'TOPAZ', 200, '노란 리본', 'avatar/item/head/ribbon-yellow-v1.png', 4, 1, NOW(6), NOW(6)),
(11, 'HEAD', 'TOPAZ', 200, '흰 리본', 'avatar/item/head/ribbon-white-v1.png', 5, 1, NOW(6), NOW(6)),
(12, 'HEAD', 'TOPAZ', 200, '초록 리본', 'avatar/item/head/ribbon-green-v1.png', 6, 1, NOW(6), NOW(6)),
(13, 'HEAD', 'TOPAZ', 200, '하늘 리본', 'avatar/item/head/ribbon-sky-v1.png', 7, 1, NOW(6), NOW(6)),

-- 옷 — 도트 잠옷 셋(유료) + 앞치마 넷(무료)
(5, 'BODY', 'GEM', 200, '회색 도트 잠옷', 'avatar/item/body/pajama-gray-v1.png', 1, 1, NOW(6), NOW(6)),
(6, 'BODY', 'GEM', 200, '보라 도트 잠옷', 'avatar/item/body/pajama-purple-v1.png', 2, 1, NOW(6), NOW(6)),
(14, 'BODY', 'GEM', 200, '하늘 도트 잠옷', 'avatar/item/body/pajama-sky-v1.png', 3, 1, NOW(6), NOW(6)),
(15, 'BODY', 'TOPAZ', 300, '초록 앞치마', 'avatar/item/body/apron-green-v1.png', 4, 1, NOW(6), NOW(6)),
(16, 'BODY', 'TOPAZ', 300, '주황 앞치마', 'avatar/item/body/apron-orange-v1.png', 5, 1, NOW(6), NOW(6)),
(17, 'BODY', 'TOPAZ', 300, '검정 앞치마', 'avatar/item/body/apron-black-v1.png', 6, 1, NOW(6), NOW(6)),
(18, 'BODY', 'TOPAZ', 300, '분홍 앞치마', 'avatar/item/body/apron-pink-v1.png', 7, 1, NOW(6), NOW(6)),
-- 카디건 넷(무료). 앞치마가 300 인데 이쪽을 200 으로 둔 것은 기획이 정한 값이다.
(26, 'BODY', 'TOPAZ', 200, '코랄 카디건', 'avatar/item/body/cardigan-coral-v1.png', 8, 1, NOW(6), NOW(6)),
(27, 'BODY', 'TOPAZ', 200, '남색 카디건', 'avatar/item/body/cardigan-navy-v1.png', 9, 1, NOW(6), NOW(6)),
(28, 'BODY', 'TOPAZ', 200, '민트 카디건', 'avatar/item/body/cardigan-mint-v1.png', 10, 1, NOW(6), NOW(6)),
(29, 'BODY', 'TOPAZ', 200, '하늘 카디건', 'avatar/item/body/cardigan-sky-v1.png', 11, 1, NOW(6), NOW(6)),

-- 손 — 베개 셋(유료) + 소품 둘 + 요술봉 넷(무료)
(7, 'HAND', 'GEM', 200, '회색 베개', 'avatar/item/hand/pillow-gray-v1.png', 1, 1, NOW(6), NOW(6)),
(8, 'HAND', 'GEM', 200, '보라 베개', 'avatar/item/hand/pillow-purple-v1.png', 2, 1, NOW(6), NOW(6)),
(19, 'HAND', 'GEM', 200, '하늘 베개', 'avatar/item/hand/pillow-sky-v1.png', 3, 1, NOW(6), NOW(6)),
(20, 'HAND', 'TOPAZ', 150, '수박', 'avatar/item/hand/watermelon-v1.png', 4, 1, NOW(6), NOW(6)),
(21, 'HAND', 'TOPAZ', 150, '비치볼', 'avatar/item/hand/beach-ball-v1.png', 5, 1, NOW(6), NOW(6)),
(22, 'HAND', 'TOPAZ', 150, '민트 요술봉', 'avatar/item/hand/wand-mint-v1.png', 6, 1, NOW(6), NOW(6)),
(23, 'HAND', 'TOPAZ', 150, '보라 요술봉', 'avatar/item/hand/wand-purple-v1.png', 7, 1, NOW(6), NOW(6)),
(24, 'HAND', 'TOPAZ', 150, '분홍 요술봉', 'avatar/item/hand/wand-pink-v1.png', 8, 1, NOW(6), NOW(6)),
(25, 'HAND', 'TOPAZ', 150, '검정 요술봉', 'avatar/item/hand/wand-black-v1.png', 9, 1, NOW(6), NOW(6)) AS new_row
ON DUPLICATE KEY UPDATE `currency`   = new_row.`currency`,
                        `price`      = new_row.`price`,
                        `name`       = new_row.`name`,
                        `image_key`  = new_row.`image_key`,
                        `sort_order` = new_row.`sort_order`,
                        `active`     = new_row.`active`,
                        `updated_at` = NOW(6);
