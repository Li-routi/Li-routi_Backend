CREATE TABLE fcm_device (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    last_registered_at DATETIME(6) NOT NULL,
    deactivated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fcm_device_token UNIQUE (token),
    CONSTRAINT fk_fcm_device_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_fcm_device_member_active (member_id, active)
);

CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    type VARCHAR(60) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(255) NOT NULL,
    group_id BIGINT NULL,
    reference_id BIGINT NULL,
    reference_type VARCHAR(40) NULL,
    deduplication_key VARCHAR(160) NOT NULL,
    read_at DATETIME(6) NULL,
    push_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    push_attempts INT NOT NULL DEFAULT 0,
    last_push_attempt_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_member_dedup UNIQUE (member_id, deduplication_key),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    INDEX idx_notification_member_category_id (member_id, category, id),
    INDEX idx_notification_created_at (created_at),
    INDEX idx_notification_push_status_id (push_status, id)
);

CREATE TABLE group_routine_verification_disappointment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_routine_verification_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_verification_disappointment UNIQUE (group_routine_verification_id, member_id),
    CONSTRAINT fk_group_verification_disappointment_verification FOREIGN KEY (group_routine_verification_id) REFERENCES group_routine_verification (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_verification_disappointment_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
);

CREATE TABLE group_poke (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    poked_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_poke_daily UNIQUE (group_id, sender_id, recipient_id, poked_date),
    CONSTRAINT fk_group_poke_group FOREIGN KEY (group_id) REFERENCES member_group (id),
    CONSTRAINT fk_group_poke_sender FOREIGN KEY (sender_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_poke_recipient FOREIGN KEY (recipient_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT ck_group_poke_not_self CHECK (sender_id <> recipient_id)
);
