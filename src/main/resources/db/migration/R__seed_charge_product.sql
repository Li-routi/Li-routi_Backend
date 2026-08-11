-- 충전·교환 상품 마스터 시드.
--
-- 내용이 바뀌면 다시 도는 파일이라 매번 실행된다. 고정 id + upsert 로 멱등하게 쓴다.
--
-- 시안의 금액과 묶음을 그대로 옮겼다. 보너스 수량은 시안에 550코인 + 50 보너스 하나만
-- 나와 있어 그 비율(약 9%)을 큰 묶음에 적용했다. 실제 값이 정해지면 이 파일을 고친다.
--
-- ⚠️ reward_currency 를 GEM 이 아닌 값으로 바꾸지 말 것. CHECK 제약이 막지만, 애초에
--    무료 재화를 현금으로 팔면 결제는 성공하고 지급만 실패한다.
--
-- 판매를 내릴 때는 아래 목록에서 줄을 지우지 말고 active 를 0 으로 바꾼다. upsert 는 추가·
-- 수정만 하므로 줄을 지워도 DB 에서는 사라지지 않고, 아무도 관리하지 않는 행이 운영에 계속
-- 노출된다. R__seed_challenge.sql 과 같은 규칙이다 — 이 파일이 마스터다.
--
-- 그래서 active 도 갱신 목록에 있다. DB 에서 직접 내리면 다음 배포가 되살리므로, 긴급히
-- 내려야 하는 경우에도 이 파일을 함께 고쳐야 한다. 가격 오류처럼 돈이 걸린 상황에서 특히
-- 중요하다.

INSERT INTO `charge_product` (`id`, `reward_currency`, `reward_amount`, `bonus_amount`,
                              `price_krw`, `popular`, `sort_order`, `active`,
                              `created_at`, `updated_at`)
VALUES (1, 'GEM', 100, 0, 1100, 0, 1, 1, NOW(6), NOW(6)),
       (2, 'GEM', 300, 0, 3300, 0, 2, 1, NOW(6), NOW(6)),
       (3, 'GEM', 500, 50, 5500, 1, 3, 1, NOW(6), NOW(6)),
       (4, 'GEM', 1000, 100, 11000, 0, 4, 1, NOW(6), NOW(6)),
       (5, 'GEM', 3000, 300, 33000, 1, 5, 1, NOW(6), NOW(6)),
       (6, 'GEM', 5000, 500, 55000, 0, 6, 1, NOW(6), NOW(6)),
       (7, 'GEM', 10000, 1000, 110000, 0, 7, 1, NOW(6), NOW(6)) AS new_row
ON DUPLICATE KEY UPDATE `reward_currency` = new_row.`reward_currency`,
                        `reward_amount`   = new_row.`reward_amount`,
                        `bonus_amount`    = new_row.`bonus_amount`,
                        `price_krw`       = new_row.`price_krw`,
                        `popular`         = new_row.`popular`,
                        `sort_order`      = new_row.`sort_order`,
                        `active`          = new_row.`active`,
                        `updated_at`      = NOW(6);

-- 교환은 GEM(유료) 을 내고 TOPAZ(무료) 를 받는다. 많이 살수록 유리하다.
INSERT INTO `exchange_product` (`id`, `from_currency`, `from_amount`, `to_currency`, `to_amount`,
                                `sort_order`, `active`, `created_at`, `updated_at`)
VALUES (1, 'GEM', 3, 'TOPAZ', 100, 1, 1, NOW(6), NOW(6)),
       (2, 'GEM', 5, 'TOPAZ', 200, 2, 1, NOW(6), NOW(6)),
       (3, 'GEM', 10, 'TOPAZ', 500, 3, 1, NOW(6), NOW(6)),
       (4, 'GEM', 20, 'TOPAZ', 1100, 4, 1, NOW(6), NOW(6)) AS new_row
ON DUPLICATE KEY UPDATE `from_currency` = new_row.`from_currency`,
                        `from_amount`   = new_row.`from_amount`,
                        `to_currency`   = new_row.`to_currency`,
                        `to_amount`     = new_row.`to_amount`,
                        `sort_order`    = new_row.`sort_order`,
                        `active`        = new_row.`active`,
                        `updated_at`    = NOW(6);
