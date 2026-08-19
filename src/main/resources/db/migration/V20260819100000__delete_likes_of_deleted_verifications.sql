-- 지워진 인증에 붙어 있는 좋아요를 정리한다.
--
-- 인증 삭제는 소프트 삭제라 행이 남고, 같은 회차에 다시 올리면 그 행이 되살아난다
-- (ChallengeVerification.reverify 가 deletedAt 을 null 로 되돌린다). 좋아요는 그 행을
-- 가리키므로, 지울 때 함께 정리하지 않으면 새로 올린 사진이 지운 사진의 좋아요를 물려받는다.
--
-- 코드는 이제 삭제 시점에 지운다(ChallengeVerificationCommandService.softDelete). 이 파일은
-- 그 코드가 없던 동안 쌓인 것을 치우는 일회성 정리다.
--
-- 작성 시점 실측으로는 운영·개발 모두 0 건이다(지워진 인증 자체가 아직 없다). 그래도 두는
-- 이유는 둘이다.
--   1. 배포까지의 사이에 생길 수 있다. 그때 지워진 인증을 되살리면 옛 좋아요가 따라온다.
--   2. 0 건이어도 "이 상태는 남아 있으면 안 된다" 를 기록으로 남긴다.
--
-- 되돌릴 수 없다. 다만 지워진 인증의 좋아요는 화면 어디에도 보이지 않고, 캐릭터 해금의
-- LIKE_GIVEN 도 cv.deleted_at IS NULL 로 이미 걸러 세지 않으므로 잃는 것이 없다.
--
-- 신고(challenge_verification_report)는 건드리지 않는다. 지워서 신고 누적을 회피하는 길이
-- 되므로 인증을 내려도 그대로 남겨야 한다.

DELETE cvl
FROM challenge_verification_like cvl
         JOIN challenge_verification cv ON cv.id = cvl.challenge_verification_id
WHERE cv.deleted_at IS NOT NULL;
