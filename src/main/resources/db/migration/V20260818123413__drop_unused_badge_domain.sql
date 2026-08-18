-- Badge 도메인은 #270(achievement.badge_image_key + 관리자 API)으로 완전히 대체됐다.
-- member_achievement.status == CLAIMED 자체가 "배지를 받았다"는 뜻이라
-- 별도 회원별 배지 보유 테이블이 필요 없었다는 게 결론이다.

ALTER TABLE achievement DROP COLUMN badge_code;

DROP TABLE IF EXISTS member_badge;
DROP TABLE IF EXISTS badge;
