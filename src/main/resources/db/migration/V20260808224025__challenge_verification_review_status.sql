-- 심사가 장애로 판정을 못 하면 인증을 보류하고 나중에 다시 심사한다.
-- 보류 건은 행을 저장하되 사진을 공개하지 않으므로, 그 상태를 행에 들고 있어야 한다.
--
-- review_status  PENDING = 승격되지 않은 보류. APPROVED = 공개된 정상 인증.
--                REJECTED 는 두지 않는다 — 반려는 행을 만들지 않고 422 로 끝나며,
--                반려 행을 남기면 유니크 키 (참여, 회차, 날짜)가 그날의 재시도를 막는다.
-- review_attempts 보류로 들어온 뒤의 재심사 시도 횟수. 최초 심사는 세지 않는다.
-- pending_since   보류가 시작된 시각. 상한(24시간) 판정의 기준이다.
--
-- 기존 행은 전부 APPROVED 다. 지금까지 저장된 인증은 심사를 지났거나 심사가 없던 시절의
-- 것이므로 보류가 아니다. 보류 건에만 값이 있으면 되는 pending_since 는 NULL 로 둔다.
ALTER TABLE `challenge_verification`
  ADD COLUMN `review_status` varchar(20) NOT NULL DEFAULT 'APPROVED' AFTER `hidden_at`,
  ADD COLUMN `review_attempts` int NOT NULL DEFAULT 0 AFTER `review_status`,
  ADD COLUMN `pending_since` datetime(6) NULL AFTER `review_attempts`;

-- 재심사 스케줄러가 "보류인 것"만 훑는다. 전체에서 PENDING 은 극소수라 인덱스가 그대로 듣는다.
-- pending_since 를 뒤에 두는 것은 상한이 지난 것부터 처리하기 위해서다.
CREATE INDEX `idx_verification_review_status`
  ON `challenge_verification` (`review_status`, `pending_since`);
