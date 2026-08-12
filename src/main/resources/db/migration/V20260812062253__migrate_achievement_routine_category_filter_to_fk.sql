-- routine_category_filter 는 'EXERCISE'/'HEALTH' 같은 자유 문자열이라, 실제
-- routine_category 테이블(고정 카테고리 id 1~6, R__seed_routine.sql)과 안정적으로
-- 매칭될 근거가 없다. 카테고리 이름이 바뀌거나 사용자가 같은 이름의 개인 카테고리를
-- 만들어도 깨지지 않도록 FK 컬럼으로 바꾼다.

-- 1) 새 컬럼 추가
ALTER TABLE achievement
    ADD COLUMN routine_category_id BIGINT NULL AFTER routine_category_filter;

-- 2) 기존 문자열 값을 고정 카테고리 id로 옮긴다.
--    R__seed_routine.sql 이 시드하는 id 그대로다: 1=운동 2=건강 3=자기계발
--    4=생활정리 5=마음관리 6=취미. 이 매핑이 바뀌면 이 UPDATE 도 같이 바뀌어야 한다.
UPDATE achievement
SET routine_category_id = CASE routine_category_filter
                              WHEN 'EXERCISE'          THEN 1
                              WHEN 'HEALTH'             THEN 2
                              WHEN 'SELF_DEVELOPMENT'   THEN 3
                              WHEN 'ORGANIZE'           THEN 4
                              WHEN 'MIND'               THEN 5
                              WHEN 'HOBBY'              THEN 6
                              ELSE NULL
    END
WHERE routine_category_filter IS NOT NULL;

-- 3) 허용되지 않은 알 수 없는 카테고리 값이 존재하는지 검증 (MySQL 전용)
-- routine_category_filter가 NULL이 아닌데 매핑 실패로 routine_category_id가 NULL이 된 행이 있다면 에러 발생 후 마이그레이션 중단
ALTER TABLE achievement
    ADD CONSTRAINT chk_migration_category_valid
        CHECK (routine_category_filter IS NULL OR routine_category_id IS NOT NULL);

ALTER TABLE achievement
DROP CONSTRAINT chk_migration_category_valid;

-- 4) 옛 문자열 컬럼 제거
ALTER TABLE achievement
DROP COLUMN routine_category_filter;

-- 5) 참조 무결성 보장 - 존재하지 않는 카테고리 id 를 가리키는 업적이 생기지 않게 한다.
ALTER TABLE achievement
    ADD CONSTRAINT fk_achievement_routine_category
        FOREIGN KEY (routine_category_id) REFERENCES routine_category (id);
