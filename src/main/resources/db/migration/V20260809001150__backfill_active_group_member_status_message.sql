-- 기존 활성 참여 관계만 기본 그룹별 상태 메시지로 채운다.
-- LEFT/KICKED 이력과 이미 설정된 메시지는 보존한다.
UPDATE group_member
SET status_message = '반가워요!'
WHERE status = 'ACTIVE'
  AND status_message IS NULL;
