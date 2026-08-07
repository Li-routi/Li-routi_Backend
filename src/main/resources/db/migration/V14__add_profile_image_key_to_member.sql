-- Member.profileImageKey 매핑 컬럼 추가.
-- null = 기본 회색 아바타(프론트에서 처리), 값이 있으면 S3 오브젝트 key.
ALTER TABLE member ADD COLUMN profile_image_key VARCHAR(2048) NULL;
