-- 챌린지 분류에 '마음관리'(MIND)를 더한다.
--
-- category 는 실제 MySQL ENUM 이라 자바 enum 에 값을 늘리는 것만으로는 부족하다. 컬럼이
-- 모르는 값을 넣으려 하면 저장이 실패한다.
--
-- 기존 다섯 값의 순서를 바꾸지 않는다. ENUM 은 내부적으로 순번으로 저장되므로, 순서를
-- 바꾸면 이미 저장된 행의 뜻이 통째로 어긋난다. MIND 를 알파벳 순서에 맞춰 끼워 넣어도
-- 되지만, 그 순간 LIFE·STUDY 의 순번이 밀린다. 그래서 맨 뒤에 붙인다.
ALTER TABLE `challenge`
    MODIFY COLUMN `category`
        ENUM('EXERCISE','HEALTH','HOBBY','LIFE','STUDY','MIND')
        COLLATE utf8mb4_unicode_ci NOT NULL;
