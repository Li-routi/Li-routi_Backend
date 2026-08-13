-- avatar_item 이 절대 URL 대신 S3 key 를 담게 한다.
--
-- 이 프로젝트의 미디어는 전부 key 를 저장하고 조회에서 resolveViewUrl 로 조립한다. 오리진이
-- AWS_S3_PUBLIC_BASE_URL 하나로 갈리기 때문인데, avatar_item 만 절대 URL 이라 그 계약 밖에
-- 있었다. 도메인을 바꾸거나 CDN 을 붙이는 순간 이 컬럼의 값이 전부 낡는다.
--
-- 지금 값은 placehold.co 더미 여섯이고 보유·착용 행이 0 건이다. 실사용 데이터가 붙기 전인
-- 지금이 가장 싸다 -- 나중에는 저장된 행을 옮기는 백필이 붙는다.
--
-- 길이도 함께 줄인다. 절대 URL 을 담으려고 2048 이었으나 key 는 훨씬 짧다.
-- 다른 미디어 컬럼과 같은 512 로 맞춘다.

ALTER TABLE `avatar_item`
    CHANGE COLUMN `image_url` `image_key` VARCHAR(512) NOT NULL;

-- 더미 값은 R__seed_avatar_item 이 곧바로 덮어쓴다(반복 마이그레이션은 이 뒤에 돈다).
-- 그래도 여기서 비워 두지 않는 이유는, 시드가 다루지 않는 행(백오피스가 넣은 것 등)이
-- 나중에 생겼을 때 NOT NULL 을 어길 수 없기 때문이다.
