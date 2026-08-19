-- 업적 보상으로만 지급되는 비매품 의상은 price=0 을 허용한다.
-- 판매 중(active=TRUE)인 아이템은 여전히 유료여야 한다는 원래 의도는 유지한다.
ALTER TABLE `avatar_item`
DROP CHECK `ck_avatar_item_price_positive`;

ALTER TABLE `avatar_item`
    ADD CONSTRAINT `ck_avatar_item_price_positive`
        CHECK (`price` > 0 OR `active` = FALSE);

INSERT INTO avatar_item (slot, currency, price, name, image_url, sort_order, active, created_at, updated_at)
VALUES
-- ACH-SP-001 모두의 응원단장 (손)
('HAND', 'TOPAZ', 0, '응원 폼폼', 'avatar/item/hand/achievement-cheer-pompom-v1.png', 100, FALSE, NOW(6), NOW(6)),
-- ACH-SP-002 100일 완주 (머리)
('HEAD', 'TOPAZ', 0, '100일 왕관', 'avatar/item/head/achievement-100days-crown-v1.png', 101, FALSE, NOW(6), NOW(6)),
-- ACH-SP-003 한 달의 루틴러 (몸)
('BODY', 'TOPAZ', 0, '파란 완주 유니폼', 'avatar/item/body/achievement-months-uniform-v1.png', 102, FALSE, NOW(6), NOW(6)),
-- ACH-SP-004 루틴 하우스 메이트 (몸)
('BODY', 'TOPAZ', 0, '토마토 셔츠', 'avatar/item/body/achievement-housemate-shirt-1-v1.png', 104, FALSE, NOW(6), NOW(6)),
-- ACH-SP-005 특별 후원자 (몸)
('BODY', 'TOPAZ', 0, '임금님 망토', 'avatar/item/body/achievement-cape-v1.png', 106, FALSE, NOW(6), NOW(6));
