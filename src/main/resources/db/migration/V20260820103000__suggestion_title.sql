-- 건의에 제목을 더한다.
--
-- 처음에는 제목을 두지 않았다. 분류가 그 자리를 대신하고 칸이 하나 늘면 건의 자체가 줄어든다고
-- 봤는데, 목록에서 무슨 건의인지 알아볼 방법이 분류 이름뿐이라 같은 분류의 건의가 전부 같아
-- 보인다. 검색도 붙일 곳이 없었다.
--
-- VARCHAR(100) 이다. TEXT 가 아닌 이유는 본문과 같다 — 상한이 있어야 요청 DTO 가 그것을
-- 검증하고, 비정상적으로 큰 값을 DB 앞에서 막는다.
--
-- 세 단계로 나눈다. 이미 쌓인 건의에는 제목이 없으므로 NOT NULL 을 바로 걸 수 없다.

ALTER TABLE `suggestion`
    ADD COLUMN `title` VARCHAR(100) NULL AFTER `suggestion_category_id`;

-- 옛 건의의 제목은 본문 앞부분으로 채운다.
--
-- 지어낸 값("제목 없음")보다 낫다 — 운영이 목록에서 무엇에 대한 건의인지 알아볼 수 있어야
-- 하는데, 그 정보가 있는 곳은 본문뿐이다. 줄바꿈은 공백으로 바꾼다. 그대로 두면 한 줄짜리
-- 제목 안에 개행이 들어가 목록이 깨진다.
--
-- 100자로 자른다. 본문이 그보다 길면 뒤가 잘리지만, 본문 자체는 그대로 남아 있어 잃는 것이 없다.
UPDATE `suggestion`
SET `title` = LEFT(REPLACE(REPLACE(`content`, '\r', ' '), '\n', ' '), 100)
WHERE `title` IS NULL;

-- 빈 본문은 없지만(NOT NULL + 등록 시 검증), 위 UPDATE 가 어떤 이유로든 빈 문자열을 남겼다면
-- NOT NULL 을 통과해 버린다. 목록에 빈 줄이 뜨는 것을 막는다.
UPDATE `suggestion`
SET `title` = '제목 없음'
WHERE `title` = '';

ALTER TABLE `suggestion`
    MODIFY COLUMN `title` VARCHAR(100) NOT NULL;

-- 검색용 인덱스를 두지 않는다.
--
-- 검색이 `title LIKE '%...%'` 라 앞이 열려 있어 B-Tree 인덱스가 쓰이지 않는다. 만들어 두면
-- 쓰이지도 않으면서 쓰기 비용만 늘고, "인덱스가 있으니 빠르겠지" 라는 오해를 남긴다.
--
-- 그래도 괜찮은 것은 조회가 언제나 member_id 로 먼저 좁혀지기 때문이다 -- 이미 있는
-- idx_suggestion_member_id 가 자기 건의만 남기고, LIKE 는 그 안에서만 돈다. 한 사람 앞에
-- 수천 건이 쌓이는 데이터가 되면 그때 전문 검색(FULLTEXT)을 따로 검토한다.
