-- 건의 분류. 앱이 제공하는 마스터 데이터다.
--
-- "어디에 대한 건의인가"(메인·그룹·챌린지…)로 나눈다. 화면 이름을 그대로 쓰므로 사용자가
-- 고민 없이 고르고, 접수된 건의가 어느 화면 것인지 분류만 보고 갈린다.
--
-- 처음에는 "어떤 종류의 건의인가"(버그·기능 제안·불편)로 나눴다. 분류 개수가 안정적이라는
-- 장점이 있었지만, 사용자는 자기 불편이 버그인지 기능 제안인지 가르기 어려워한다. 화면
-- 단위는 그 판단이 필요 없다. 화면이 늘면 분류도 늘어나는 것은 감수한다.
--
-- 고정 id + upsert 다. 매 배포마다 실행되므로 멱등해야 한다.
--
-- id 를 재사용하지 않는다. 이미 그 분류로 보낸 건의가 다른 뜻을 가리키게 된다.
-- 더 이상 받지 않을 분류는 지우지 말고 active = 0 으로 내린다. upsert 는 추가·수정만 하므로
-- 여기서 줄을 지워도 DB 에서는 사라지지 않는다 — 지운 채로 두면 그 행만 시드 밖으로 샌다.

INSERT INTO suggestion_category
    (id, code, name, display_order, active, created_at, updated_at)
VALUES
    -- 현재 받는 분류
    (5, 'MAIN', '메인', 1, TRUE, NOW(6), NOW(6)),
    (6, 'GROUP', '그룹', 2, TRUE, NOW(6), NOW(6)),
    (7, 'GROUP_CHAT', '그룹채팅', 3, TRUE, NOW(6), NOW(6)),
    (8, 'CHALLENGE', '챌린지', 4, TRUE, NOW(6), NOW(6)),
    -- 기타는 뜻이 그대로라 id 를 유지한다. 이미 이 분류로 보낸 건의가 계속 유효하다.
    (4, 'ETC', '기타', 5, TRUE, NOW(6), NOW(6)),

    -- 내린 분류. 지우지 않는 이유는 위 주석 참고 — 이 분류로 보낸 건의가 참조를 잃는다.
    -- 관리 화면에서 뒤로 가도록 display_order 만 밀어 둔다.
    (1, 'BUG', '버그 신고', 90, FALSE, NOW(6), NOW(6)),
    (2, 'FEATURE', '기능 제안', 91, FALSE, NOW(6), NOW(6)),
    (3, 'INCONVENIENCE', '불편 사항', 92, FALSE, NOW(6), NOW(6))
    AS new_row
ON DUPLICATE KEY UPDATE
    code          = new_row.code,
    name          = new_row.name,
    display_order = new_row.display_order,
    active        = new_row.active,
    updated_at    = NOW(6);
