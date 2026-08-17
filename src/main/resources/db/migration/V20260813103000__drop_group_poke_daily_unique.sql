-- 그룹 poke는 같은 날 동일 송신자·수신자 조합에도 횟수 제한 없이 누적한다.
-- 각 요청은 별도 이력과 알림·업적 이벤트의 근거가 되므로 일일 유니크 제약을 제거한다.
-- 기존 UNIQUE 인덱스는 group_id 외래키의 보조 인덱스도 겸하므로 대체 인덱스를 먼저 둔다.
ALTER TABLE group_poke
    ADD INDEX idx_group_poke_group_id (group_id),
    DROP INDEX uk_group_poke_daily;
