-- ==================== 시작 업적 (20개, 각 50토파즈) ====================
INSERT INTO achievement (code, category, name, condition_desc, progress_type, target_count, routine_category_filter, topaz_reward, badge_yn, limited_outfit_yn, sort_order) VALUES
('ACH-ST-001', 'START', '첫 좋아요',        '친구 인증에 처음 좋아요 누르기',                 'NONE',              NULL, NULL,               50, FALSE, FALSE, 1),
('ACH-ST-002', 'START', '첫 쿡쿡',          '친구에게 처음 쿡쿡 보내기 성공',                 'NONE',              NULL, NULL,               50, FALSE, FALSE, 2),
('ACH-ST-003', 'START', '첫 방 만들기',      '루틴방을 처음 만들기',                          'NONE',              NULL, NULL,               50, FALSE, FALSE, 3),
('ACH-ST-004', 'START', '첫 초대 참여',      '초대코드로 방에 처음 참여하기',                  'NONE',              NULL, NULL,               50, FALSE, FALSE, 4),
('ACH-ST-005', 'START', '첫 리포트 확인',    '월간 리포트 상세를 처음 열기',                   'NONE',              NULL, NULL,               50, FALSE, FALSE, 5),
('ACH-ST-006', 'START', '첫 토파즈 교환',    '다이아를 토파즈로 처음 교환하기',                'NONE',              NULL, NULL,               50, FALSE, FALSE, 6),
('ACH-ST-007', 'START', '인증 5회',         '루틴 완료 누적 5회',                            'CUMULATIVE_COUNT',   5, NULL,               50, FALSE, FALSE, 7),
('ACH-ST-008', 'START', '인증 10회',        '루틴 완료 누적 10회',                           'CUMULATIVE_COUNT',  10, NULL,               50, FALSE, FALSE, 8),
('ACH-ST-009', 'START', '인증 30회',        '루틴 완료 누적 30회',                           'CUMULATIVE_COUNT',  30, NULL,               50, FALSE, FALSE, 9),
('ACH-ST-010', 'START', '좋아요 10회',      '친구 인증에 누른 좋아요 누적 10회',              'CUMULATIVE_COUNT',  10, NULL,               50, FALSE, FALSE, 10),
('ACH-ST-011', 'START', '좋아요 30회',      '친구 인증에 누른 좋아요 누적 30회',              'CUMULATIVE_COUNT',  30, NULL,               50, FALSE, FALSE, 11),
('ACH-ST-012', 'START', '쿡쿡 5회',         '쿡쿡 보내기 성공 누적 5회',                      'CUMULATIVE_COUNT',   5, NULL,               50, FALSE, FALSE, 12),
('ACH-ST-013', 'START', '쿡쿡 10회',        '쿡쿡 보내기 성공 누적 10회',                     'CUMULATIVE_COUNT',  10, NULL,               50, FALSE, FALSE, 13),
('ACH-ST-014', 'START', '방 참여 2개',      '직접 만든 방과 초대 참여 방을 합쳐 서로 다른 방 2개 참여', 'DISTINCT_ROOM_COUNT', 2, NULL,          50, FALSE, FALSE, 14),
('ACH-ST-015', 'START', '운동 시작',        '운동 루틴 첫 완료',                             'NONE',              NULL, 'EXERCISE',         50, FALSE, FALSE, 15),
('ACH-ST-016', 'START', '건강 시작',        '건강 루틴 첫 완료',                             'NONE',              NULL, 'HEALTH',           50, FALSE, FALSE, 16),
('ACH-ST-017', 'START', '자기계발 시작',     '자기계발 루틴 첫 완료',                         'NONE',              NULL, 'SELF_DEVELOPMENT', 50, FALSE, FALSE, 17),
('ACH-ST-018', 'START', '정리 시작',        '생활정리 루틴 첫 완료',                         'NONE',              NULL, 'ORGANIZE',         50, FALSE, FALSE, 18),
('ACH-ST-019', 'START', '마음 시작',        '마음관리 루틴 첫 완료',                         'NONE',              NULL, 'MIND',             50, FALSE, FALSE, 19),
('ACH-ST-020', 'START', '취미 시작',        '취미 루틴 첫 완료',                             'NONE',              NULL, 'HOBBY',            50, FALSE, FALSE, 20);

-- ==================== 달성 업적 (3개, 각 100토파즈 + 배지) ====================
INSERT INTO achievement (code, category, name, condition_desc, progress_type, target_count, routine_category_filter, topaz_reward, badge_yn, limited_outfit_yn, sort_order) VALUES
('ACH-AC-001', 'ACHIEVE', '첫 인증',      '개인 체크 또는 사진 인증으로 루틴 첫 완료',       'NONE',              NULL, NULL, 100, TRUE, FALSE, 21),
('ACH-AC-002', 'ACHIEVE', '꾸준한 사람',  '개인 체크와 사진 인증 합산 누적 50회',            'CUMULATIVE_COUNT',   50, NULL, 100, TRUE, FALSE, 22),
('ACH-AC-003', 'ACHIEVE', '특별 후원가',  '다이아 상품 첫 모의 구매 성공. 다이아→토파즈 교환 제외', 'NONE',        NULL, NULL, 100, TRUE, FALSE, 23);

-- ==================== 스페셜 업적 (1개, 200토파즈 + 배지 + 한정 의상) ====================
INSERT INTO achievement (code, category, name, condition_desc, progress_type, target_count, routine_category_filter, topaz_reward, badge_yn, limited_outfit_yn, sort_order) VALUES
('ACH-SP-001', 'SPECIAL', '모두의 응원단장', '쿡쿡 보내기 100회와 친구 인증 좋아요 100회를 모두 달성', 'COMPOSITE', NULL, NULL, 200, TRUE, TRUE, 24);

-- SP-001 복합 조건 상세 (진행도를 조건별로 각각 표시하기 위함)
INSERT INTO achievement_condition (achievement_id, condition_key, target_count, sort_order)
SELECT id, 'POKE_COUNT', 100, 1 FROM achievement WHERE code = 'ACH-SP-001';

INSERT INTO achievement_condition (achievement_id, condition_key, target_count, sort_order)
SELECT id, 'LIKE_COUNT', 100, 2 FROM achievement WHERE code = 'ACH-SP-001';
