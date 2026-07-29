-- 인증 게시물 좋아요 테이블 (#63).
--
-- 좋아요는 챌린지가 아니라 인증 한 건(challenge_verification.id)에 붙는다.
-- 같은 사진이 피드에도 내 인증 목록에도 나오지만 같은 행이므로 좋아요 수도 하나다.
--
-- 취소는 행 삭제(하드 삭제)다. 소프트 삭제를 쓰면 취소 후 다시 누를 때 남아 있는 행이
-- 유니크 제약에 걸린다 — MySQL 유니크 제약은 deleted_at IS NULL을 모른다.
-- challenge_verification에 deleted_at을 두지 않은 것과 같은 논거이며, 좋아요는 신고와 달리
-- 취소가 일상적으로 반복되는 동작이라 이 문제가 바로 드러난다(database-schema.md).
--
-- 유니크 제약이 중복을 막는다. 다만 신고와 달리 위반을 409로 바꾸지 않고 성공으로 처리한다.
-- 좋아요는 토글이라 따닥 누르는 것이 정상 사용이다.
--
-- 이 제약은 피드 집계의 인덱스이기도 하다. 페이지의 인증 id 목록으로
-- WHERE challenge_verification_id IN (...) GROUP BY 하므로 선두 컬럼을 그대로 탄다.
-- 그래서 별도 인덱스를 두지 않는다. "내가 좋아요한 목록" 화면이 생기면 그때
-- (member_id, ...) 인덱스를 검토한다.
--
-- V3와 같은 이유로 IF NOT EXISTS를 쓰지 않는다. ddl-auto가 validate인 상태에서 처음
-- 들어오는 테이블이라 자가 치유할 대상이 없다. 예상 밖으로 이미 있다면 조용히 넘기지 말고
-- 부팅을 실패시킨다 — 제약이 빠진 채 이력만 남으면 중복 방지와 집계 인덱스가 함께 사라진다.

CREATE TABLE `challenge_verification_like` (
  `challenge_verification_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verification_like_verification_member` (`challenge_verification_id`,`member_id`),
  KEY `FK_verification_like_member` (`member_id`),
  CONSTRAINT `FK_verification_like_verification` FOREIGN KEY (`challenge_verification_id`) REFERENCES `challenge_verification` (`id`),
  CONSTRAINT `FK_verification_like_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
