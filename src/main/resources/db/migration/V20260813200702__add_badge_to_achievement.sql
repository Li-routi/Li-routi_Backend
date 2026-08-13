ALTER TABLE achievement ADD COLUMN badge_code VARCHAR(30) NULL;

CREATE TABLE badge (
                       id BIGINT PRIMARY KEY,
                       code VARCHAR(30) NOT NULL,
                       name VARCHAR(50) NOT NULL,
                       image_key VARCHAR(512) NOT NULL,
                       active BOOLEAN NOT NULL,
                       created_at DATETIME NOT NULL,
                       updated_at DATETIME NOT NULL,
                       CONSTRAINT uk_badge_code UNIQUE (code)
);

CREATE TABLE member_badge (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              member_id BIGINT NOT NULL,
                              badge_id BIGINT NOT NULL,
                              earned_at DATETIME NOT NULL,
                              created_at DATETIME NOT NULL,
                              updated_at DATETIME NOT NULL,
                              CONSTRAINT uk_member_badge UNIQUE (member_id, badge_id),
                              CONSTRAINT fk_member_badge_member FOREIGN KEY (member_id) REFERENCES member (id),
                              CONSTRAINT fk_member_badge_badge FOREIGN KEY (badge_id) REFERENCES badge (id)
);