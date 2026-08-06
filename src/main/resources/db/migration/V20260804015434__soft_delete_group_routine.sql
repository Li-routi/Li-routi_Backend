-- 완료·미이행 할당과 인증 이력의 FK를 유지하면서 그룹 루틴을 삭제 상태로 전환한다(#30).
-- 기존 (group_id, title) 유니크 제약은 그대로 유지하므로 삭제된 루틴의 제목도 재사용하지 않는다.
ALTER TABLE `group_routine`
  ADD COLUMN `active` bit(1) NOT NULL DEFAULT b'1' AFTER `description`;
