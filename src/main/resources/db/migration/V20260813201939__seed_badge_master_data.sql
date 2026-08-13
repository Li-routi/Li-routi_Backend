-- 배지 마스터 데이터 시드. badge.code 는 achievement.code 를 그대로 재사용한다 -
-- 배지와 업적이 1:1 관계라 별도 코드 체계를 만들 이유가 없다.
--
-- image_key 는 실제 S3 업로드 경로가 정해지기 전까지의 placeholder다. 형식은
-- 기존 avatar 자산과 통일: badge/{achievement_code_lowercase}.png
-- 실제 파일이 업로드되면 이 값만 UPDATE 하면 된다 - 코드 변경 불필요.

-- ==================== 달성 업적(EPIC) 배지 9개 ====================
INSERT INTO badge (id, code, name, image_key, active, created_at, updated_at) VALUES
                                                                                  (1, 'ACH-AC-001', '사진 프레임과 체크 도장',        'badge/ach-ac-001.png', TRUE, NOW(), NOW()),
                                                                                  (2, 'ACH-AC-002', '쌓여 있는 체크 스티커',          'badge/ach-ac-002.png', TRUE, NOW(), NOW()),
                                                                                  (3, 'ACH-AC-004', '뜯어진 달력과 체크 도장',        'badge/ach-ac-004.png', TRUE, NOW(), NOW()),
                                                                                  (4, 'ACH-AC-005', '일주일 달력과 별 스티커',        'badge/ach-ac-005.png', TRUE, NOW(), NOW()),
                                                                                  (5, 'ACH-AC-006', '손가락 쿡 표시와 진동선',        'badge/ach-ac-006.png', TRUE, NOW(), NOW()),
                                                                                  (6, 'ACH-AC-007', '여러 개의 아얏 말풍선',          'badge/ach-ac-007.png', TRUE, NOW(), NOW()),
                                                                                  (7, 'ACH-AC-008', '카테고리 아이콘이 붙은 지도',    'badge/ach-ac-008.png', TRUE, NOW(), NOW()),
                                                                                  (8, 'ACH-AC-009', '불이 켜진 루틴방',               'badge/ach-ac-009.png', TRUE, NOW(), NOW()),
                                                                                  (9, 'ACH-AC-010', '방 안을 채운 캐릭터 얼굴',       'badge/ach-ac-010.png', TRUE, NOW(), NOW());

-- ==================== 스페셜 업적(UNIQUE) 배지 5개 ====================
INSERT INTO badge (id, code, name, image_key, active, created_at, updated_at) VALUES
                                                                                  (10, 'ACH-SP-001', '손가락 쿡, 응원 스파크, 하트',        'badge/ach-sp-001.png', TRUE, NOW(), NOW()),
                                                                                  (11, 'ACH-SP-002', '100 달력, 왕관, 체크 도장',           'badge/ach-sp-002.png', TRUE, NOW(), NOW()),
                                                                                  (12, 'ACH-SP-003', '집 모양 달력과 켜진 창문',            'badge/ach-sp-003.png', TRUE, NOW(), NOW()),
                                                                                  (13, 'ACH-SP-004', '여러 캐릭터가 모인 집',               'badge/ach-sp-004.png', TRUE, NOW(), NOW()),
                                                                                  (14, 'ACH-SP-005', '코인, 선물 상자, THANKS 도장',        'badge/ach-sp-005.png', TRUE, NOW(), NOW());

-- ==================== achievement.badge_code 백필 ====================
UPDATE achievement SET badge_code = code
WHERE code IN (
               'ACH-AC-001', 'ACH-AC-002', 'ACH-AC-004', 'ACH-AC-005', 'ACH-AC-006',
               'ACH-AC-007', 'ACH-AC-008', 'ACH-AC-009', 'ACH-AC-010',
               'ACH-SP-001', 'ACH-SP-002', 'ACH-SP-003', 'ACH-SP-004', 'ACH-SP-005'
    );