-- GROUP_DISTINCT_DAY_COUNT(루틴 하우스 메이트)가 "전원이 함께 인증한 날"을 하루에 한 번만
-- 세도록 막는 근거 컬럼. member_achievement_progress_day 가 회원 단위로 하는 일을
-- 그룹 단위로 대신한다 - 별도 테이블 대신 컬럼으로 두는 이유는 그룹당 업적당 "가장 최근에
-- 센 날짜" 하나만 있으면 충분해서다(회원처럼 날짜 목록 전체를 남길 필요가 없다).
ALTER TABLE group_achievement_progress
    ADD COLUMN last_counted_date DATE NULL AFTER current_progress;
