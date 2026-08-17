ALTER TABLE member
    ADD COLUMN routine_deadline_notification_enabled BIT(1) NOT NULL DEFAULT b'1',
    ADD COLUMN new_verification_notification_enabled BIT(1) NOT NULL DEFAULT b'1',
    ADD COLUMN verification_reaction_notification_enabled BIT(1) NOT NULL DEFAULT b'1',
    ADD COLUMN poke_notification_enabled BIT(1) NOT NULL DEFAULT b'1',
    ADD COLUMN new_chat_notification_enabled BIT(1) NOT NULL DEFAULT b'1',
    ADD COLUMN like_notification_enabled BIT(1) NOT NULL DEFAULT b'1';
