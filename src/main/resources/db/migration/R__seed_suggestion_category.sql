-- 건의 분류. 앱이 제공하는 마스터 데이터다.
--
-- "무엇에 대한 건의인가"(루틴·챌린지·상점…)가 아니라 "어떤 종류의 건의인가"로 나눈다.
-- 화면 단위로 나누면 화면이 늘 때마다 분류가 늘고, 여러 화면에 걸친 건의를 넣을 자리가 없다.
--
-- 고정 id + upsert 다. 매 배포마다 실행되므로 멱등해야 한다.
--
-- id 를 재사용하지 않는다. 이미 그 분류로 보낸 건의가 다른 뜻을 가리키게 된다.
-- 더 이상 받지 않을 분류는 지우지 말고 active = 0 으로 내린다.

INSERT INTO suggestion_category
    (id, code, name, display_order, active, created_at, updated_at)
VALUES (1, 'BUG', '버그 신고', 1, TRUE, NOW(6), NOW(6)),
       (2, 'FEATURE', '기능 제안', 2, TRUE, NOW(6), NOW(6)),
       (3, 'INCONVENIENCE', '불편 사항', 3, TRUE, NOW(6), NOW(6)),
       (4, 'ETC', '기타', 4, TRUE, NOW(6), NOW(6))
    AS new_row
ON DUPLICATE KEY UPDATE
    code          = new_row.code,
    name          = new_row.name,
    display_order = new_row.display_order,
    active        = new_row.active,
    updated_at    = NOW(6);
