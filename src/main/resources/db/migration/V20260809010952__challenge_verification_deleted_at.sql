-- 인증 게시글 삭제. 행을 지우지 않고 내려간 시각만 남긴다.
--
-- 하드 삭제가 아닌 이유는 둘이다. 좋아요·신고가 이 행을 NOT NULL 로 참조하고, 특히
-- 신고당한 글을 작성자가 지워 신고 이력까지 없애는 것은 곤란하다. 그리고 "그날 인증했다"는
-- 사실은 남아야 한다 — 지운 뒤 다시 인증할 수 있으면 하루 1회가 뚫린다.
--
-- 기존 행은 전부 NULL 이다. 지금까지 저장된 인증은 아무도 지운 적이 없다.
--
-- ⚠️ 유니크 키 (member_challenge_id, participation_round, verified_date) 는 그대로 둔다.
-- 소프트 삭제가 유니크 제약과 부딪히지 않는 이유는 당일 재인증이 INSERT 가 아니라 기존 행
-- UPDATE 이기 때문이다. 그 조회가 지운 행도 찾아야 성립한다(database-schema.md).
ALTER TABLE `challenge_verification`
  ADD COLUMN `deleted_at` datetime(6) NULL AFTER `pending_since`;
