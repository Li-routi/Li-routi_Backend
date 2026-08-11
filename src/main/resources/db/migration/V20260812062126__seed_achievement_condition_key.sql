-- ==================== 시작 업적 ====================
-- 첫 액션형 (NONE) - 해당 액션 이벤트의 conditionKey 와 동일한 값을 써서
-- 누적형 업적과 같은 이벤트를 공유한다.
UPDATE achievement SET condition_key = 'LIKE_COUNT'                        WHERE code = 'ACH-ST-001'; -- 첫 좋아요
UPDATE achievement SET condition_key = 'POKE_COUNT'                        WHERE code = 'ACH-ST-002'; -- 첫 쿡쿡
UPDATE achievement SET condition_key = 'ROOM_CREATE_COUNT'                 WHERE code = 'ACH-ST-003'; -- 첫 방 만들기
UPDATE achievement SET condition_key = 'ROOM_JOIN_COUNT'                   WHERE code = 'ACH-ST-004'; -- 첫 초대 참여
UPDATE achievement SET condition_key = 'REPORT_VIEW_COUNT'                 WHERE code = 'ACH-ST-005'; -- 첫 리포트 확인
UPDATE achievement SET condition_key = 'DIAMOND_TO_TOPAZ_EXCHANGE_COUNT'   WHERE code = 'ACH-ST-006'; -- 첫 토파즈 교환

-- 누적형 (CUMULATIVE_COUNT)
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-007'; -- 인증 5회
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-008'; -- 인증 10회
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-009'; -- 인증 30회
UPDATE achievement SET condition_key = 'LIKE_COUNT'             WHERE code = 'ACH-ST-010'; -- 좋아요 10회
UPDATE achievement SET condition_key = 'LIKE_COUNT'             WHERE code = 'ACH-ST-011'; -- 좋아요 30회
UPDATE achievement SET condition_key = 'POKE_COUNT'             WHERE code = 'ACH-ST-012'; -- 쿡쿡 5회
UPDATE achievement SET condition_key = 'POKE_COUNT'             WHERE code = 'ACH-ST-013'; -- 쿡쿡 10회

-- 서로 다른 방 수 (DISTINCT_ROOM_COUNT) - 방 생성/참여 이벤트 둘 다 이 키로 발행돼야 한다.
UPDATE achievement SET condition_key = 'ROOM_DISTINCT_COUNT' WHERE code = 'ACH-ST-014'; -- 방 참여 2개

-- 카테고리 시작 업적 (NONE + routine_category_filter)
-- 주의: 전부 동일한 ROUTINE_COMPLETE_COUNT 키를 쓰지만 routine_category_filter 로 서로 구분된다.
-- AchievementProgressService 가 아직 이 필터를 검사하지 않으므로, 이 값만으로는
-- "운동 루틴 완료" 이벤트가 잘못 6개 전부를 달성 처리할 수 있다 (코드 쪽 추가 작업 필요, 아래 설명 참고).
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-015'; -- 운동 시작
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-016'; -- 건강 시작
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-017'; -- 자기계발 시작
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-018'; -- 정리 시작
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-019'; -- 마음 시작
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT' WHERE code = 'ACH-ST-020'; -- 취미 시작

-- ==================== 달성 업적 ====================
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'            WHERE code = 'ACH-AC-001'; -- 첫 인증
UPDATE achievement SET condition_key = 'ROUTINE_COMPLETE_COUNT'            WHERE code = 'ACH-AC-002'; -- 꾸준한 사람
UPDATE achievement SET condition_key = 'DIAMOND_PRODUCT_MOCK_PURCHASE_COUNT' WHERE code = 'ACH-AC-003'; -- 특별 후원가 (다이아→토파즈 교환은 별도 키 ST-006 과 구분)

-- ==================== 스페셜 업적 ====================
-- ACH-SP-001(모두의 응원단장)은 COMPOSITE 라 이 컬럼을 쓰지 않는다.
-- achievement_condition 테이블에 이미 POKE_COUNT / LIKE_COUNT 로 정의돼 있다.
