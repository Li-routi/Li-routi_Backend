-- 기획 변경: 시작 업적(RARE)도 이제 배지를 지급한다.
-- 기존에는 배지가 EPIC·UNIQUE 카테고리에만 있었으나, RARE 20개도 배지 대상으로 추가한다.
-- 비활성(active=FALSE) 업적은 대상에서 제외한다 - 화면에 노출 안 되는 업적에 배지를 매길 이유가 없다.
UPDATE achievement
SET badge_yn = TRUE
WHERE category = 'RARE'
  AND active = TRUE;
