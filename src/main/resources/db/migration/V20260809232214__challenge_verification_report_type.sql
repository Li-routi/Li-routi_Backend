-- 신고에 사유 선택을 붙인다.
--
-- 지금은 자유 입력 reason 하나뿐이라 신고를 모아 봐도 무엇 때문인지 알 수 없다. 대부분 비어
-- 있고, 채워져 있어도 자유 문장이라 셀 수가 없다(운영 4건 중 사유가 있는 것은 1건이다).
--
-- NULL 을 허용하는 것이 요점이다. NOT NULL 로 두려면 기존 행을 무엇으로든 채워야 하는데,
-- ETC 로 채우면 "사용자가 기타를 고른 것" 과 "그때는 사유 자체가 없던 것" 이 통계에서
-- 섞인다. UNKNOWN 같은 값을 따로 두면 클라이언트가 절대 보내지 않을 값이 enum 에 남는다.
-- 컬럼은 NULL 을 허용하되 요청 DTO 가 필수로 막으므로, 새 신고에는 반드시 값이 들어간다.
-- 옛 행이 NULL 인 것은 "그때는 없던 값" 이라는 사실 그대로다.
--
-- 길이는 VARCHAR(20) 이다. 지금 값 중 가장 긴 것이 IRRELEVANT(10자)라 여유가 두 배다.
-- ENUM 타입을 쓰지 않는 이유는 값을 추가할 때마다 ALTER 가 필요하기 때문이다 — 이 저장소는
-- 다른 enum 컬럼도 전부 VARCHAR 로 두고 애플리케이션이 검증한다.

ALTER TABLE `challenge_verification_report`
    ADD COLUMN `report_type` VARCHAR(20) NULL AFTER `reporter_id`;

-- 인덱스를 두지 않는다. 사유는 숨김 판정에 쓰이지 않고(건수만 센다), 통계 조회도 아직 없다.
-- 필요해지면 그때 (challenge_verification_id, report_type) 을 검토한다.
