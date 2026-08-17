-- 얼굴 슬롯을 걷어낸다.
--
-- 의상이 전 캐릭터 공통 실루엣이라 얼굴 자리를 따로 두면 캐릭터 얼굴과 겹치는 그림이 되고,
-- 자산도 그만큼 더 든다. 슬롯을 셋(HEAD·BODY·HAND)으로 줄인다.
--
-- ⚠️ 행을 반드시 지워야 한다. 자바 enum 에서 FACE 를 뺐기 때문에, slot = 'FACE' 인 행이
--    하나라도 남아 있으면 그 행을 읽는 순간 enum 변환에서 터진다. 상점 목록 조회가 통째로
--    죽는다는 뜻이다.
--
-- 지우는 순서는 참조의 역순이다. member_avatar_equipment 가 (avatar_item_id, slot) 복합
-- 외래 키로 avatar_item 을 참조하고, member_avatar_item 은 avatar_item_id 를 참조한다.
-- 마스터를 먼저 지우려 하면 외래 키에 걸려 실패한다.
--
-- 운영에는 보유·착용 행이 0 건인 것을 확인하고 지운다. 시드 아이템이 placehold.co 더미라
-- 아직 아무도 사지 않았다. 산 사람이 있는 환경(로컬 더미 등)에서는 그 보유가 함께 지워지는데,
-- 지우지 않으면 위의 enum 변환 문제로 앱이 아예 뜨지 않으므로 남길 수가 없다.

DELETE
FROM `member_avatar_equipment`
WHERE `slot` = 'FACE';

DELETE
FROM `member_avatar_item`
WHERE `avatar_item_id` IN (SELECT `id` FROM `avatar_item` WHERE `slot` = 'FACE');

DELETE
FROM `avatar_item`
WHERE `slot` = 'FACE';
